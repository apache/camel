/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.component.tahu.handlers;

import java.util.List;
import java.util.function.BiConsumer;

import org.eclipse.tahu.host.HostApplication;
import org.eclipse.tahu.message.PayloadDecoder;
import org.eclipse.tahu.message.SparkplugBPayloadDecoder;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.Message;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugMeta;
import org.eclipse.tahu.model.MqttServerDefinition;
import org.eclipse.tahu.mqtt.RandomStartupDelay;

public class TahuHostApplication extends HostApplication {

    TahuHostApplication(
            TahuHostApplicationEventHandler eventHandler,
            String hostId,
            List<String> sparkplugSubscriptions,
            List<MqttServerDefinition> serverDefinitions,
            RandomStartupDelay randomStartupDelay,
            PayloadDecoder<SparkplugBPayload> payloadDecoder,
            boolean onlineState) {
        super(
                eventHandler,
                hostId,
                sparkplugSubscriptions,
                serverDefinitions,
                randomStartupDelay,
                payloadDecoder,
                onlineState);
    }

    public void startup() {
        start(true);
    }

    public static final class HostApplicationBuilder {

        private String hostId;
        private List<String> sparkplugSubscriptions = List.of(SparkplugMeta.SPARKPLUG_B_TOPIC_PREFIX + "/#");
        private List<MqttServerDefinition> serverDefinitions;
        private RandomStartupDelay randomStartupDelay = null;
        private PayloadDecoder<SparkplugBPayload> payloadDecoder = new SparkplugBPayloadDecoder();
        private boolean onlineState = true;

        private BiConsumer<EdgeNodeDescriptor, Message> onMessageConsumer;
        private BiConsumer<EdgeNodeDescriptor, Metric> onMetricConsumer;

        private volatile TahuHostApplication tahuHostApplication;

        public HostApplicationBuilder() {}

        public HostApplicationBuilder hostId(String hostId) {
            checkBuildState();
            this.hostId = hostId;
            return this;
        }

        public HostApplicationBuilder sparkplugSubscriptions(List<String> sparkplugSubscriptions) {
            checkBuildState();
            this.sparkplugSubscriptions = List.copyOf(sparkplugSubscriptions);
            return this;
        }

        public HostApplicationBuilder serverDefinitions(List<MqttServerDefinition> serverDefinitions) {
            checkBuildState();
            this.serverDefinitions = List.copyOf(serverDefinitions);
            return this;
        }

        public HostApplicationBuilder onlineState(boolean onlineState) {
            checkBuildState();
            this.onlineState = onlineState;
            return this;
        }

        public HostApplicationBuilder onMessageConsumer(BiConsumer<EdgeNodeDescriptor, Message> onMessageConsumer) {
            checkBuildState();
            this.onMessageConsumer = onMessageConsumer;
            return this;
        }

        public HostApplicationBuilder onMetricConsumer(BiConsumer<EdgeNodeDescriptor, Metric> onMetricConsumer) {
            checkBuildState();
            this.onMetricConsumer = onMetricConsumer;
            return this;
        }

        private void checkBuildState() throws IllegalStateException {
            if (tahuHostApplication != null) {
                throw new IllegalStateException(
                        "Unable to reuse a HostApplicationBuilder for multiple TahuHostApplication instances");
            }
        }

        public TahuHostApplication build() {
            TahuHostApplication cachedTahuHostApplication = tahuHostApplication;

            if (cachedTahuHostApplication == null) {
                TahuHostApplicationEventHandler eventHandler =
                        new TahuHostApplicationEventHandler(onMessageConsumer, onMetricConsumer);

                cachedTahuHostApplication = tahuHostApplication = new TahuHostApplication(
                        eventHandler,
                        hostId,
                        sparkplugSubscriptions,
                        serverDefinitions,
                        randomStartupDelay,
                        payloadDecoder,
                        onlineState);
            }

            return cachedTahuHostApplication;
        }
    }
}
