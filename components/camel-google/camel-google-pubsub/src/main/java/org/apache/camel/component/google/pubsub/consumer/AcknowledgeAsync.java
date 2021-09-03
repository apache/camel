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
package org.apache.camel.component.google.pubsub.consumer;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Synchronization;

public class AcknowledgeAsync implements Synchronization {

    private final AckReplyConsumer ackReplyConsumer;

    public AcknowledgeAsync(AckReplyConsumer ackReplyConsumer) {
        this.ackReplyConsumer = ackReplyConsumer;
    }

    @Override
    public void onComplete(Exchange exchange) {
        ackReplyConsumer.ack();
    }

    @Override
    public void onFailure(Exchange exchange) {
        ackReplyConsumer.nack();
    }
}
