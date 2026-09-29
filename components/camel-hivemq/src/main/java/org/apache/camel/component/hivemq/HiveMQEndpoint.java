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
package org.apache.camel.component.hivemq;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

@UriEndpoint(firstVersion = "4.23.0", scheme = "hivemq", title = "HiveMQ", syntax = "hivemq:topic",
             category = { Category.MESSAGING, Category.IOT }, headersClass = HiveMQConstants.class)
public class HiveMQEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    /**
     * The MQTT topic name or pattern to subscribe to or publish on.
     */
    @UriPath
    @Metadata(required = true)
    private String topic;

    /**
     * The HiveMQ component configuration options.
     */
    @UriParam
    @Metadata(description = "To use a custom HiveMQConfiguration")
    private HiveMQConfiguration configuration;

    public HiveMQEndpoint(String uri, HiveMQComponent component, HiveMQConfiguration configuration, String topic) {
        super(uri, component);
        this.configuration = configuration;
        this.topic = topic;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HiveMQProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        HiveMQConsumer consumer = new HiveMQConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    HiveMQClientAdapter createClient() {
        return switch (configuration.getMqttVersion()) {
            case MQTT_3_1_1 -> new Mqtt3ClientAdapter(configuration);
            case MQTT_5_0 -> new Mqtt5ClientAdapter(configuration);
        };
    }

    void connect(HiveMQClientAdapter client) {
        try {
            client.connect(configuration.isCleanStart())
                    .orTimeout(HiveMQConstants.DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            client.stop();
            throw unwrapConnectFailure(e);
        }
    }

    private static RuntimeCamelException unwrapConnectFailure(CompletionException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof TimeoutException) {
            return new RuntimeCamelException("Timed out connecting to the HiveMQ broker", cause);
        }
        return new RuntimeCamelException("Failed to connect to the HiveMQ broker", cause);
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public HiveMQConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(HiveMQConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getHost() + ":" + configuration.getPort();
    }

    @Override
    public String getServiceProtocol() {
        return "mqtt";
    }
}
