#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

ARG BASE_IMAGE=eclipse-temurin:21-jdk

FROM $BASE_IMAGE

ARG IMAGE_ARCH
ARG JBANG_VERSION
ARG CAMEL_VERSION

ENV JBANG_VERSION=$JBANG_VERSION \
    CAMEL_VERSION=$CAMEL_VERSION \
    JBANG_PATH=/opt/jbang

RUN mkdir $JBANG_PATH && chown ubuntu:ubuntu $JBANG_PATH

ENV PATH="$PATH:$JBANG_PATH"

USER ubuntu

# Download JBang, create camel exec, set JBang version, trust camel sources and download camel DEPS by running camel
RUN bash -c set -o pipefail && wget -qc https://github.com/jbangdev/jbang/releases/download/v$JBANG_VERSION/jbang.tar -O - | \
    tar xf - --strip-components=2 -C $JBANG_PATH jbang/bin/jbang jbang/bin/jbang.jar
RUN curl -L \
    https://raw.githubusercontent.com/apache/camel/camel-${CAMEL_VERSION}/dsl/camel-jbang/camel-jbang-main/dist/CamelJBang.java \
    -o ${JBANG_PATH}/CamelJBang.java && \
    sed -E -i \
        "s/\\$\\{camel\\.jbang\\.version:[^}]+\\}/\\\${camel.jbang.version:${CAMEL_VERSION}}/g; \
        s/\\$\\{camel-kamelets\\.version:[^}]+\\}/\\\${camel-kamelets.version:${CAMEL_VERSION}}/g" \
        ${JBANG_PATH}/CamelJBang.java
RUN echo '#!/bin/sh\nexec jbang run ${JBANG_PATH}/CamelJBang.java "$@"' > $JBANG_PATH/camel && \
    chmod +x $JBANG_PATH/camel && \
    mkdir -p $HOME/.jbang/cache && echo -n $JBANG_VERSION > $HOME/.jbang/cache/version.txt && \
    jbang trust add https://github.com/apache/camel && \
    camel version

ENTRYPOINT ["camel"]
