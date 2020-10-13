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

import java.util.ArrayList;
import java.util.List;

import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.pubsub.v1.AcknowledgeRequest;
import org.apache.camel.Exchange;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.spi.Synchronization;

public class AcknowledgeSync implements Synchronization {

    private final SubscriberStub subscriber;
    private final String subscriptionName;

    public AcknowledgeSync(SubscriberStub subscriber, String subscriptionName) {
        this.subscriber = subscriber;
        this.subscriptionName = subscriptionName;
    }

    @Override
    public void onComplete(Exchange exchange) {
        AcknowledgeRequest ackRequest = AcknowledgeRequest.newBuilder()
                .addAllAckIds(getAckIdList(exchange))
                .setSubscription(subscriptionName).build();
        try {
            subscriber.acknowledgeCallable().call(ackRequest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onFailure(Exchange exchange) {
    }

    private List<String> getAckIdList(Exchange exchange) {
        List<String> ackList = new ArrayList<>();

        if (exchange.getIn().getBody() instanceof List) {
            for (Object body : exchange.getIn().getBody(List.class)) {
                if (body instanceof Exchange) {
                    String ackId = exchange.getIn().getHeader(GooglePubsubConstants.ACK_ID, String.class);
                    if (null != ackId) {
                        ackList.add(ackId);
                    }
                }
            }
        }

        String ackId = exchange.getIn().getHeader(GooglePubsubConstants.ACK_ID, String.class);
        if (null != ackId) {
            ackList.add(ackId);
        }

        return ackList;
    }
}
