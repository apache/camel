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
package org.apache.camel.component.sjms;

import java.security.SecureRandom;
import java.util.UUID;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.component.sjms.jms.ConnectionResource;
import org.apache.camel.component.sjms.jms.SessionPool;
import org.apache.camel.impl.DefaultConsumer;

/**
 * TODO Add Class documentation for SjmsConsumer
 */
public class SjmsConsumer extends DefaultConsumer {

    public SjmsConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    /**
     * @return
     */
    protected String createId() {
        String answer = null;
        SecureRandom ng = new SecureRandom();
        UUID uuid = new UUID(ng.nextLong(), ng.nextLong());
        answer = uuid.toString();
        return answer;
    }

    protected SjmsEndpoint getSjmsEndpoint() {
        return (SjmsEndpoint)this.getEndpoint();
    }

    protected ConnectionResource getConnectionResource() {
        return getSjmsEndpoint().getConnectionResource();
    }

    protected SessionPool getSessionPool() {
        return getSjmsEndpoint().getSessions();
    }

    public int getAcknowledgementMode() {
        return getSjmsEndpoint().getAcknowledgementMode().intValue();
    }

    public boolean isTransacted() {
        return getSjmsEndpoint().isTransacted();
    }

    public boolean isSynchronous() {
        return getSjmsEndpoint().isSynchronous();
    }

    public String getDestinationName() {
        return getSjmsEndpoint().getDestinationName();
    }

    public int getConsumerCount() {
        return getSjmsEndpoint().getConsumerCount();
    }

    public boolean isTopic() {
        return getSjmsEndpoint().isTopic();
    }

    public String getMessageSelector() {
        return getSjmsEndpoint().getMessageSelector();
    }

    public String getDurableSubscriptionId() {
        return getSjmsEndpoint().getDurableSubscriptionId();
    }

    public TransactionCommitStrategy getCommitStrategy() {
        return getSjmsEndpoint().getCommitStrategy();
    }

    public int getTransactionBatchCount() {
        return getSjmsEndpoint().getTransactionBatchCount();
    }
}
