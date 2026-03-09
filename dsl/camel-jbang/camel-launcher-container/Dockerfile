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

FROM ${container.base-image}

LABEL org.opencontainers.image.title="Apache Camel Launcher" \
      org.opencontainers.image.description="Self-contained executable JAR for running Apache Camel JBang without requiring JBang installed" \
      org.opencontainers.image.version="${camel-launcher.version}" \
      org.opencontainers.image.vendor="Apache Software Foundation" \
      org.opencontainers.image.authors="dev@camel.apache.org" \
      org.opencontainers.image.url="https://camel.apache.org/" \
      org.opencontainers.image.documentation="https://camel.apache.org/manual/camel-jbang.html" \
      org.opencontainers.image.source="https://github.com/apache/camel" \
      org.opencontainers.image.licenses="Apache-2.0"

# Set Camel version
ENV CAMEL_VERSION=${camel-launcher.version}

# Install the Camel Launcher
ENV CAMEL_HOME=/opt/camel
RUN mkdir -p $CAMEL_HOME/bin && chown -R ${container.user}:${container.user} $CAMEL_HOME

ENV PATH="$PATH:$CAMEL_HOME/bin"

USER ${container.user}

COPY --chown=${container.user}:${container.user} camel-launcher-${CAMEL_VERSION}.jar $CAMEL_HOME/bin/camel-launcher.jar
COPY --chown=${container.user}:${container.user} run.sh $CAMEL_HOME/bin/camel

RUN chmod +x $CAMEL_HOME/bin/camel && \
    camel version

ENTRYPOINT ["camel"]
