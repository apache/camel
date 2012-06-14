/**
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
package org.apache.camel.component.mqtt;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.fusesource.mqtt.client.QoS;

public class MQTTProducer extends DefaultAsyncProducer implements Processor {

    private final MQTTEndpoint mqttEndpoint;

    public MQTTProducer(MQTTEndpoint mqttEndpoint) {
        super(mqttEndpoint);
        this.mqttEndpoint = mqttEndpoint;

    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback asyncCallback) {
        try {
            doProcess(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        asyncCallback.done(true);
        return true;
    }

    void doProcess(Exchange exchange) throws Exception {
        byte[] body = exchange.getIn().getBody(byte[].class);
        if (body != null) {
            MQTTConfiguration configuration = mqttEndpoint.getConfiguration();
            boolean retain = configuration.isByDefaultRetain();

            if (exchange.getProperty(configuration.getMqttRetainPropertyName()) != null) {
                retain = exchange.getProperty(configuration.getMqttRetainPropertyName(), Boolean.class);
            }
            QoS qoS = configuration.getQoS();
            Object qoSValue = exchange.getProperty(configuration.getMqttQosPropertyName());
            if (qoSValue != null) {
                qoS = MQTTConfiguration.getQoS(qoSValue.toString());
            }

            String topicName = configuration.getPublishTopicName();
            Object topicValue = exchange.getProperty(configuration.getMqttTopicPropertyName());
            if (topicValue != null) {
                topicName = topicValue.toString();
            }
            mqttEndpoint.publish(topicName, body, qoS, retain);
        }
    }
}
