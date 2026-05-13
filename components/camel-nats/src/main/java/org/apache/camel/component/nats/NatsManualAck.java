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
package org.apache.camel.component.nats;

import java.time.Duration;

/**
 * Allows manual acknowledgment of JetStream messages when using the NATS consumer with {@code manualAck=true}.
 *
 * @see NatsConstants#NATS_MANUAL_ACK
 */
public interface NatsManualAck {

    /**
     * Acknowledge the message.
     */
    void ack();

    /**
     * Negative acknowledge the message. The message will be redelivered immediately.
     */
    void nak();

    /**
     * Negative acknowledge the message with a delay before redelivery.
     */
    void nakWithDelay(Duration delay);

    /**
     * Terminate delivery of this message. The server will stop redelivering it.
     */
    void term();

    /**
     * Signal that processing is still in progress. Resets the {@code ackWait} timer to prevent the server from
     * redelivering while long-running processing is ongoing.
     */
    void inProgress();
}
