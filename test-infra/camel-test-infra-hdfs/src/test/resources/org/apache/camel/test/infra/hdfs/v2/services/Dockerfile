# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
#  this work for additional information regarding copyright ownership.
#  The ASF licenses this file to You under the Apache License, Version 2.0
#  (the "License"); you may not use this file except in compliance with
#  the License.  You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
ARG FROMIMAGE_BUILDER
ARG FROMIMAGE
FROM ${FROMIMAGE_BUILDER} as builder
ARG HADOOP_VERSION
ENV HADOOP_VERSION ${HADOOP_VERSION:-2.10.2}
RUN curl --retry 3 --retry-connrefused https://archive.apache.org/dist/hadoop/common/hadoop-${HADOOP_VERSION}/hadoop-${HADOOP_VERSION}.tar.gz -o hadoop.tar.gz
RUN mkdir -p hadoop && tar --strip-components=1 -xvf hadoop.tar.gz -C /hadoop && rm -f hadoop.tar.gz
ADD core-site.xml /hadoop/etc/hadoop/core-site.xml
ADD hdfs-site.xml /hadoop/etc/hadoop/hdfs-site.xml
ADD run-datanode.sh /hadoop
ADD run-namenode.sh /hadoop

ARG FROMIMAGE
FROM $FROMIMAGE
LABEL maintainer="orpiske@apache.org"
ARG HADOOP_VERSION
ENV HADOOP_VERSION ${HADOOP_VERSION:-2.10.2}
EXPOSE 8020 9000 50010 50020 50070 50075
ENV JAVA_HOME /etc/alternatives/jre
ENV HADOOP_HOME /hadoop
ENV HADOOP_LOG_DIR=${HADOOP_HOME}/logs
COPY --from=builder /hadoop /hadoop
RUN dnf install -y java-1.8.0-openjdk-headless tar gzip rsync which procps-ng && \
    chmod +x /hadoop/*.sh && \
    dnf clean all

# Uncomment this line for enabling debug log
# ENV HADOOP_ROOT_LOGGER=DEBUG,RFA,console
VOLUME /hdfs/
WORKDIR /hadoop

# To run this image manually, follow these steps:

# Create a network:
# docker network create hadoop-tmp

# Namenode starts with (format + runtime):
# bin/hdfs --config /hadoop/etc/hadoop/  namenode -format && bin/hdfs --config /hadoop/etc/hadoop/  namenode

# All in one shot from docker:
# docker run --rm -it -p 8020:8020 -p 9000:9000 -p 50070:50070 --network hadoop-tmp --name namenode hadoop /hadoop/run-namenode.sh

# Datanode starts with:
# docker run -p 50075:50075 -p 50010:50010 -p 50020:50020 --rm -it --network hadoop-tmp --name datanode-1 hadoop /hadoop/run-datanode.sh datanode-1

# If you need more nodes, just increase the number on the datanode name (ie.: datanode-2, datanode-3, etc)s
# docker run --rm -it --network hadoop-tmp --name datanode-2 hadoop /hadoop/run-datanode.sh
