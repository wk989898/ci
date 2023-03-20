FROM golang:1.20.2

# install build essential
RUN apt-get update && \
    apt-get install -y build-essential unzip && \
    apt-get clean all

# install bazel tool
RUN curl -fsSL "https://github.com/bazelbuild/bazelisk/releases/download/v1.16.0/bazelisk-linux-amd64" -o /usr/local/bin/bazel && chmod +x /usr/local/bin/bazel
