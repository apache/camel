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
package org.apache.camel.component.azure.servicebus;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.models.BrokeredMessage;
import com.microsoft.windowsazure.services.servicebus.models.ReceiveMessageOptions;
import com.microsoft.windowsazure.services.servicebus.models.ReceiveSubscriptionMessageResult;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SbTopicConsumer extends AbstractSbConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(SbComponent.class);

    public SbTopicConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected BrokeredMessage pollBrokeredMessage(ReceiveMessageOptions opts) throws ServiceException {
        LOG.debug("SbTopicConsumer#pollBrokeredMessage");
        ReceiveSubscriptionMessageResult subscriptionMessageResult = getClient().receiveSubscriptionMessage(getConfiguration().getTopicPath(), getConfiguration().getSubscriptionName(), opts);
        LOG.debug("SbTopicConsumer#pollBrokeredMessage topicPath:" + getConfiguration().getTopicPath() + "; SubscriptionName:" + getConfiguration().getSubscriptionName());

        return subscriptionMessageResult.getValue();
    }
}
