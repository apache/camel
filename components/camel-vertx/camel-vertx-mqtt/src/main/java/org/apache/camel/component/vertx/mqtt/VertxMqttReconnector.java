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

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connects the client again after a lost or failed connection, with a delay that starts at {@code reconnectDelay} and
 * doubles up to {@code maxReconnectDelay}.
 * <p>
 * The Vert.x MQTT client does not reconnect by itself yet (vert-x3/vertx-mqtt#197). This class is the whole of the
 * reconnect logic of the component, kept apart so that it can be dropped once the client reconnects on its own: the
 * connection asks it to {@link #schedule()} an attempt after a loss or a failed connect, tells it when a connect
 * {@link #succeeded()}, and {@link #cancel() cancels} it when it stops. What the component does after every connect,
 * subscribing again among it, stays in {@link VertxMqttConnection}, since it happens on the first connect as well.
 * <p>
 * Every method runs on the context of the connection.
 */
final class VertxMqttReconnector {

    private static final Logger LOG = LoggerFactory.getLogger(VertxMqttReconnector.class);

    private final Vertx vertx;
    private final BrokerAddress broker;
    private final long initialDelay;
    private final long maxDelay;
    private final Runnable connect;

    private long timer = -1;
    private long nextDelay;
    private boolean cancelled;

    /**
     * @param initialDelay the delay of the first attempt in milliseconds
     * @param maxDelay     the longest delay in milliseconds, the delay never grows beyond it
     * @param connect      makes one attempt to connect, on the context of the connection
     */
    VertxMqttReconnector(final Vertx vertx, final BrokerAddress broker, final long initialDelay, final long maxDelay,
                         final Runnable connect) {
        this.vertx = vertx;
        this.broker = broker;
        this.initialDelay = Math.max(1, initialDelay);
        this.maxDelay = maxDelay;
        this.connect = connect;
        this.nextDelay = this.initialDelay;
    }

    /**
     * Schedules the next attempt, unless one is scheduled already or the reconnector was cancelled.
     */
    void schedule() {
        if (cancelled || timer != -1) {
            return;
        }
        final long delay = nextDelay;
        nextDelay = Math.min(nextDelay * 2, Math.max(delay, maxDelay));
        LOG.debug("Reconnecting to the MQTT broker {} in {} ms", broker, delay);
        timer = vertx.setTimer(delay, id -> {
            timer = -1;
            if (!cancelled) {
                connect.run();
            }
        });
    }

    /**
     * Starts the next series of attempts with the initial delay again.
     */
    void succeeded() {
        nextDelay = initialDelay;
    }

    /**
     * Cancels the scheduled attempt and every later one.
     */
    void cancel() {
        cancelled = true;
        if (timer != -1) {
            vertx.cancelTimer(timer);
            timer = -1;
        }
    }

    /**
     * @return the delay of the next attempt in milliseconds
     */
    long nextDelay() {
        return nextDelay;
    }
}
