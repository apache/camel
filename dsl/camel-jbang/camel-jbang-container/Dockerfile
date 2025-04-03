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

FROM docker.io/eclipse-temurin:21-jdk

ENV JBANG_VERSION=0.125.1
# /!\ Camel version must be changed in the entrypoint line too
ENV CAMEL_VERSION=4.10.3

RUN wget -c https://github.com/jbangdev/jbang/releases/download/v$JBANG_VERSION/jbang.tar -O - | tar xv && \
    chmod +x jbang/bin/jbang
ENV PATH="${PATH}:/jbang/bin"
ENV JBANG_PATH=/jbang/bin

RUN jbang trust add https://github.com/apache/camel

# used to initiliaze dependencies in the docker image
RUN jbang -Dcamel.jbang.version=$CAMEL_VERSION camel@apache/camel version
    
ENTRYPOINT [ "jbang", "-Dcamel.jbang.version=4.10.3", "camel@apache/camel"]

