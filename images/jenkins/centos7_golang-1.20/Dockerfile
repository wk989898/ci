# docker build . -t hub.pingcap.net/jenkins/centos7_golang-1.20:latest
FROM hub.pingcap.net/jenkins/centos7_jenkins

USER root
WORKDIR /root

RUN yum remove git -y && curl https://setup.ius.io | sh || true
RUN yum install --setopt=skip_missing_names_on_install=False -y git236 python-pip python3-pip

RUN pip install s3cmd requests==2.26.0 certifi==2021.10.8 && pip3 install requests

ENV GOLANG_VERSION 1.20.2
ENV GOLANG_DOWNLOAD_URL https://dl.google.com/go/go$GOLANG_VERSION.linux-amd64.tar.gz
ENV GOLANG_DOWNLOAD_SHA256 4eaea32f59cde4dc635fbc42161031d13e1c780b87097f4b4234cfce671f1768

RUN curl -fsSL "$GOLANG_DOWNLOAD_URL" -o golang.tar.gz \
    && echo "$GOLANG_DOWNLOAD_SHA256  golang.tar.gz" | sha256sum -c - \
    && tar -C /usr/local -xzf golang.tar.gz \
    && rm golang.tar.gz

RUN mkdir /go && chown jenkins:jenkins /go

RUN curl -fsSL "http://pingcap-dev.hk.ufileos.com/jenkins/jenkins-slave-docker-sqllogictest.tar.gz" | tar xz -C "/git" \
    && chown -R jenkins:jenkins /git

ENV GOPATH /go
ENV GOROOT /usr/local/go
ENV PATH $GOPATH/bin:$GOROOT/bin:$PATH

USER jenkins
WORKDIR /home/jenkins
