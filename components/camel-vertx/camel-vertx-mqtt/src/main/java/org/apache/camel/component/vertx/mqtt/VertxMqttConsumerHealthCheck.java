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
package org.apache.camel.component.vertx.mqtt;

import java.util.Map;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckResultBuilder;

/**
 * Whether the consumer receives from its broker. With automatic reconnect the consumer starts while the broker cannot
 * be reached and keeps trying, this check tells why it receives nothing: {@code DOWN} with the reason of the broker
 * until the connection and the subscription succeed, {@code UNKNOWN} while the first attempt is still under way or the
 * consumer is not started.
 */
public class VertxMqttConsumerHealthCheck implements HealthCheck {

    private final VertxMqttConsumer consumer;
    private final String id;
    private boolean enabled = true;

    public VertxMqttConsumerHealthCheck(VertxMqttConsumer consumer, String routeId) {
        this.consumer = consumer;
        this.id = "consumer:" + routeId;
    }

    @Override
    public String getGroup() {
        return "camel";
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isLiveness() {
        // A broker that cannot be reached is no reason to restart the application
        return false;
    }

    @Override
    public Result call(Map<String, Object> options) {
        HealthCheckResultBuilder builder = HealthCheckResultBuilder.on(this)
                .detail(ENDPOINT_URI, consumer.getEndpoint().getEndpointBaseUri());
        VertxMqttConnection connection = consumer.connection();
        if (connection == null) {
            return builder.unknown().message("The consumer is not started").build();
        }
        Throwable failure = connection.failure();
        if (failure != null) {
            return builder.down().message(failure.getMessage()).error(failure).build();
        }
        if (!connection.isConnected()) {
            return builder.unknown().message("Connecting to the MQTT broker " + connection.broker()).build();
        }
        return builder.up().build();
    }
}
