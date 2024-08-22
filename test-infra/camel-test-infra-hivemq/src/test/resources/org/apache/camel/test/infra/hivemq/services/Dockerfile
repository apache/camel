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

ARG HIVEMQ_VERSION=2024.6

FROM hivemq/hivemq-ce:$HIVEMQ_VERSION as hivemq-ce-sparkplug-tck

ARG SPARKPLUG_TCK_VERSION=3.0.0

USER 0

RUN set -x \
	&& apt-get update && apt-get install -y --no-install-recommends unzip \
	&& curl -fSL "https://download.eclipse.org/sparkplug/${SPARKPLUG_TCK_VERSION}/Eclipse-Sparkplug-TCK-${SPARKPLUG_TCK_VERSION}.zip" -o /opt/tck.zip \
	&& unzip -d /opt /opt/tck.zip \
	&& rm /opt/tck.zip \
	&& cp /opt/SparkplugTCK/hivemq-configuration/* /opt/hivemq/conf/ \
	&& unzip -d /opt/hivemq/extensions /opt/SparkplugTCK/hivemq-extension/sparkplug-tck-${SPARKPLUG_TCK_VERSION}.zip \
	&& rm -rf /var/lib/apt/lists/*

