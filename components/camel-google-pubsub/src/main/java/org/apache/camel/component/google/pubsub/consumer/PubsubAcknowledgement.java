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
package org.apache.camel.component.google.pubsub.consumer;

import java.util.List;

import com.google.api.client.repackaged.com.google.common.base.Strings;
import com.google.api.services.pubsub.model.AcknowledgeRequest;
import com.google.api.services.pubsub.model.ModifyAckDeadlineRequest;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class PubsubAcknowledgement {

    protected Logger logger;
    
    private final String subscriptionFullName;
    private final GooglePubsubEndpoint endpoint;

    public PubsubAcknowledgement(GooglePubsubEndpoint endpoint) {
        super();
        this.endpoint = endpoint;
        this.subscriptionFullName = String.format("projects/%s/subscriptions/%s", endpoint.getProjectId(), endpoint.getDestinationName());

        String loggerId = endpoint.getLoggerId();

        if (Strings.isNullOrEmpty(loggerId)) {
            loggerId = this.getClass().getName();
        }

        logger = LoggerFactory.getLogger(loggerId);
    }

    void acknowledge(List<String> ackIdList) {
        AcknowledgeRequest ackRequest = new AcknowledgeRequest()
                .setAckIds(ackIdList);
        try {
            endpoint.getPubsub()
                    .projects()
                    .subscriptions()
                    .acknowledge(subscriptionFullName, ackRequest)
                    .execute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void resetAckDeadline(List<String> ackIdList, Integer seconds) {

        ModifyAckDeadlineRequest nackRequest = new ModifyAckDeadlineRequest()
                .setAckIds(ackIdList)
                .setAckDeadlineSeconds(seconds);

        try {
            endpoint.getPubsub()
                    .projects()
                    .subscriptions()
                    .modifyAckDeadline(subscriptionFullName, nackRequest)
                    .execute();
        } catch (Exception e) {
            // It will timeout automatically on the channel
            logger.warn("Unable to reset ack deadline " + ackIdList, e);
        }
    }
}


