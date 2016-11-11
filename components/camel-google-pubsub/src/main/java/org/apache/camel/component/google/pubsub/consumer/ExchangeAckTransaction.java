/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.google.pubsub.consumer;

import org.apache.camel.Exchange;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.apache.camel.spi.Synchronization;

import java.util.ArrayList;
import java.util.List;

public class ExchangeAckTransaction extends PubsubAcknowledgement implements Synchronization {

    public ExchangeAckTransaction(GooglePubsubEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void onComplete(Exchange exchange) {
        acknowledge(getAckIdList(exchange));
    }

    @Override
    public void onFailure(Exchange exchange) {
        resetAckDeadline(getAckIdList(exchange));
    }

    private List<String> getAckIdList(Exchange exchange) {
        List<String> ackList = new ArrayList<>();

        if (null != exchange.getProperty(Exchange.GROUPED_EXCHANGE)) {
            for (Exchange ex : (List<Exchange>) exchange.getProperty(Exchange.GROUPED_EXCHANGE)) {
                String ackId = (String) ex.getIn().getHeader(GooglePubsubConstants.ACK_ID);
                if (null != ackId)
                    ackList.add(ackId);
            }
        } else {
            ackList.add((String) exchange.getIn().getHeader(GooglePubsubConstants.ACK_ID));
        }

        return ackList;
    }
}


