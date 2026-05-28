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
# Multi-stage layered Dockerfile for Spring Boot (container-optimized).
#
# Uses Spring Boot's built-in layer extraction to produce optimized Docker layers.
# Dependencies (most stable) are copied first, application code (most volatile) last.
#
# ./mvnw clean package
# docker build -f src/main/docker/Dockerfile -t [=ArtifactId]:[=Version] .
# docker run -it [=ArtifactId]:[=Version]
#
###

# Stage 1: Extract Spring Boot layers
FROM registry.access.redhat.com/ubi9/openjdk-25:1.24 AS builder
WORKDIR /builder
COPY target/[=AppJar] application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Stage 2: Build the optimized image
FROM registry.access.redhat.com/ubi9/openjdk-25-runtime:1.24

COPY --from=builder --chown=185 /builder/extracted/dependencies/ /deployments/
COPY --from=builder --chown=185 /builder/extracted/spring-boot-loader/ /deployments/
COPY --from=builder --chown=185 /builder/extracted/snapshot-dependencies/ /deployments/
COPY --from=builder --chown=185 /builder/extracted/application/ /deployments/

# Uncomment to expose any given port
# EXPOSE 8080
USER 185
WORKDIR /deployments

ENTRYPOINT ["java", "-jar", "application.jar"]
