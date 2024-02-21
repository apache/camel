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

FROM $FROMIMAGE

ARG CAMEL_REF=main
ARG KEEP_RUNNING=true
ARG SSH_PASSWORD=jbang

#update os and install tools
RUN dnf update -y \
    && dnf install java-17-openjdk-devel openssh-server xauth -y

RUN ssh-keygen -A

ADD 99-ssh-jbang.conf /etc/ssh/sshd_config.d/

#allow wheel group member to be trusted
RUN	sed -i "s|#auth		sufficient	pam_wheel.so trust use_uid|auth		sufficient	pam_wheel.so trust use_uid|g" /etc/pam.d/su

#create new user
RUN useradd -s /bin/bash jbang

#to avoid prompt su - root password
RUN usermod -a -G wheel jbang

USER jbang
ENV HOME=/home/jbang
WORKDIR $HOME

ENV JAVA_HOME=/usr/lib/jvm/java

RUN curl -Ls https://sh.jbang.dev | bash -s - app setup \
    && source ~/.bashrc \
    && jbang version --update \
    && jbang trust add https://github.com/apache/camel/ \
    && jbang app install camel@apache/camel/$CAMEL_REF

LABEL "camel.ref"=$CAMEL_REF
LABEL "keep.container.running"=$KEEP_RUNNING

ENV PATH=/home/jbang/.jbang/bin:$PATH

ADD entrypoint.sh /opt/

ENV CAMEL_REF=$CAMEL_REF
ENV KEEP_RUNNING=$KEEP_RUNNING

USER root
RUN echo "jbang:$SSH_PASSWORD" | chpasswd
RUN chown jbang /opt/entrypoint.sh \
    && chmod +x /opt/entrypoint.sh

EXPOSE 22

USER jbang
ENTRYPOINT ["/opt/entrypoint.sh"]
