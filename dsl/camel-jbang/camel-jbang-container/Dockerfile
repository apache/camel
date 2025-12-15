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

FROM eclipse-temurin:21-jdk

# Set Versions, JBang Path and commit ID of the specific version of dsl/camel-jbang/camel-jbang-main/dist/CamelJBang.java
ENV JBANG_VERSION=0.135.1 \
    CAMEL_VERSION=4.16.0 \
    CAMEL_JBANG_COMMIT=ef3dc3fab7c8ca87f307e85358b3f738468f369b \
    JBANG_PATH=/usr/local/bin

# Download JBang, create camel exec, set JBang version, trust camel sources and download camel DEPS by running camel
RUN bash -c set -o pipefail && wget -qc https://github.com/jbangdev/jbang/releases/download/v$JBANG_VERSION/jbang.tar -O - | tar xf - --strip-components=2 -C /usr/local/bin jbang/bin/jbang jbang/bin/jbang.jar && \
    echo '#!/bin/sh\nexec jbang run https://github.com/apache/camel/blob/'$CAMEL_JBANG_COMMIT'/dsl/camel-jbang/camel-jbang-main/dist/CamelJBang.java "$@"' > /usr/local/bin/camel && \
    chmod +x /usr/local/bin/camel && \
    mkdir -p $HOME/.jbang/cache && echo -n $JBANG_VERSION > $HOME/.jbang/cache/version.txt && \
    jbang trust add https://github.com/apache/camel && \
    camel version

ENTRYPOINT ["camel"]
