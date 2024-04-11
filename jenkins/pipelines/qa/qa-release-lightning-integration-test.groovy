// REF: https://www.jenkins.io/doc/book/pipeline/syntax/#declarative-pipeline
// Keep small than 400 lines: https://issues.jenkins.io/browse/JENKINS-37984
// should triggerd for master branches

final K8S_NAMESPACE = "jenkins-tidb"
final GIT_FULL_REPO_NAME = 'pingcap/tidb'
final POD_TEMPLATE_FILE = 'pipelines/pingcap/tidb/latest/pod-pull_lightning_integration_test.yaml'

pipeline {
    agent {
        kubernetes {
            namespace K8S_NAMESPACE
            yamlFile POD_TEMPLATE_FILE
            defaultContainer 'golang'
        }
    }
    environment {
        FILE_SERVER_URL = 'http://fileserver.pingcap.net'
    }
    options {
        timeout(time: 60, unit: 'MINUTES')
        // parallelsAlwaysFailFast()
    }
    stages {
        stage('Debug info') {
            steps {
                sh label: 'Debug info', script: """
                    printenv
                    echo "-------------------------"
                    go env
                    echo "-------------------------"
                    echo "debug command: kubectl -n ${K8S_NAMESPACE} exec -ti ${NODE_NAME} bash"
                """
                container(name: 'net-tool') {
                    sh 'dig github.com'
                }
            }
        }
        stage('Checkout') {
            options { timeout(time: 5, unit: 'MINUTES') }
            steps {
                dir("tidb") {
                    checkout (changelog: false, poll: false, scm: [
                        $class           : 'GitSCM',
                        branches         : [[name: "${params.RELEASE_BRANCH}"]],
                        userRemoteConfigs: [[
                                                refspec: "+refs/heads/${params.RELEASE_BRANCH}" + ":refs/remotes/origin/${params.RELEASE_BRANCH}",
                                                url: 'https://github.com/pingcap/tidb.git',
                                             ]],
                        extensions: [[$class: 'PruneStaleBranch'], [$class: 'CleanBeforeCheckout'],
                                [$class: 'CloneOption',shallow: true,depth:   1,timeout: 10]],
                    ])
                }
            }
        }
        stage('Prepare') {
            steps {
                dir("third_party_download") {
                    retry(2) {
                        sh label: "download third_party", script: """
                            chmod +x ../tidb/lightning/tests/*.sh
                            ${WORKSPACE}/tidb/lightning/tests/download_integration_test_binaries.sh ${params.RELEASE_BRANCH}
                            mkdir -p bin && mv third_bin/* bin/
                            ls -alh bin/
                            ./bin/pd-server -V
                            ./bin/tikv-server -V
                            ./bin/tiflash --version
                        """
                    }
                }
                dir('tidb') {
                    sh label: "check all tests added to group", script: """#!/usr/bin/env bash
                        chmod +x lightning/tests/*.sh
                        ./lightning/tests/run_group_lightning_tests.sh others
                    """
                    // build lightning.test for integration test
                    // only build binarys if not exist, use the cached binarys if exist
                    sh label: "prepare", script: """
                        [ -f ./bin/tidb-server ] || make
                        [ -f ./bin/tidb-lightning.test ] || make build_for_lightning_integration_test
                        ls -alh ./bin
                        ./bin/tidb-server -V
                    """
                    cache(path: "./", includes: '**/*', key: "ws/${BUILD_TAG}/lightning-test") { 
                        sh label: "prepare", script: """
                            cp -r ../third_party_download/bin/* ./bin/
                            ls -alh ./bin
                        """
                    }
                }
            }
        }
        stage('Tests') {
            matrix {
                axes {
                    axis {
                        name 'TEST_GROUP'
                        values 'G00', 'G01', 'G02', 'G03', 'G04', 'G05', 'G06',  'G07', 'G08'
                    }
                }
                agent{
                    kubernetes {
                        namespace K8S_NAMESPACE
                        defaultContainer 'golang'
                        yamlFile POD_TEMPLATE_FILE
                    }
                }
                stages {
                    stage("Test") {
                        environment { CODECOV_TOKEN = credentials('codecov-token-tidb') }
                        options { timeout(time: 45, unit: 'MINUTES') }
                        steps {
                            dir('tidb') {
                                cache(path: "./", includes: '**/*', key: "ws/${BUILD_TAG}/lightning-test") { 
                                    sh label: "TEST_GROUP ${TEST_GROUP}", script: """#!/usr/bin/env bash
                                        chmod +x lightning/tests/*.sh
                                        ./lightning/tests/run_group_lightning_tests.sh ${TEST_GROUP}
                                    """  
                                }
                            }
                        }
                        post{
                            failure {
                                sh label: "collect logs", script: """
                                    ls /tmp/lightning_test
                                    tar -cvzf log-${TEST_GROUP}.tar.gz \$(find /tmp/lightning_test/ -type f -name "*.log")
                                    ls -alh  log-${TEST_GROUP}.tar.gz  
                                """
                                archiveArtifacts artifacts: "log-${TEST_GROUP}.tar.gz", fingerprint: true 
                            }
                        }
                    }
                }
            }
        }
    }
}