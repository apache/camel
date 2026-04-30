<#--

    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->
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

####
# This Dockerfile is used in order to build a container that runs the Camel application
#
# ./mvnw clean package
# docker build -f src/main/docker/Dockerfile -t [=ArtifactId]:[=Version] .
# docker run -it [=ArtifactId]:[=Version]
#
###
FROM eclipse-temurin:25-jre-ubi9-minimal

ENV LANGUAGE='en_US:en'

RUN mkdir /deployments

COPY --chown=185 target/[=AppJar] /deployments/

# Uncomment to expose any given port
# EXPOSE 8080
USER 185
# Uncomment to provide any Java option
# ENV JAVA_OPTS=""
WORKDIR /deployments

ENTRYPOINT exec java $JAVA_OPTS -jar [=AppJar]
