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

ARG FROMIMAGE
FROM $FROMIMAGE as artemis-base
LABEL maintainer="orpiske@apache.org"
ARG ARTEMIS_VERSION
ENV ARTEMIS_VERSION ${ARTEMIS_VERSION:-2.18.0}
ARG ARTEMIS_JOURNAL
ENV ARTEMIS_JOURNAL ${ARTEMIS_JOURNAL:-aio}
ENV JMS_BROKER_ROOT /opt/camel-kafka-connector/artemis/
EXPOSE 1883 5672 8161 61616
RUN dnf install -y java-11-openjdk-headless libaio tar gzip && \
    dnf clean all && \
    mkdir -p ${JMS_BROKER_ROOT} && \
    cd ${JMS_BROKER_ROOT} && \
    curl --retry 3 --retry-connrefused https://archive.apache.org/dist/activemq/activemq-artemis/${ARTEMIS_VERSION}/apache-artemis-${ARTEMIS_VERSION}-bin.tar.gz -o apache-artemis.tar.gz && \
    mkdir -p apache-artemis && tar --strip-components=1 --exclude=examples/* -xvf apache-artemis.tar.gz -C apache-artemis && \
    rm -f apache-artemis.tar.gz
WORKDIR ${JMS_BROKER_ROOT}
ENV JAVA_HOME /etc/alternatives/jre

FROM artemis-base as artemis
RUN ${JMS_BROKER_ROOT}/apache-artemis/bin/artemis create --${ARTEMIS_JOURNAL} --relax-jolokia --allow-anonymous --http-host 0.0.0.0 --user admin --password "admin" --role amq --data /artemis-storage ${JMS_BROKER_ROOT}/apache-artemis-instance
CMD [ "sh", "-c", "./apache-artemis-instance/bin/artemis run" ]
