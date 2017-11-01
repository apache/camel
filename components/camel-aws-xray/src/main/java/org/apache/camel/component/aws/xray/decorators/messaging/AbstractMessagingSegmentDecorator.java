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
package org.apache.camel.component.aws.xray.decorators.messaging;

import com.amazonaws.xray.entities.Entity;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.aws.xray.decorators.AbstractSegmentDecorator;

public abstract class AbstractMessagingSegmentDecorator extends AbstractSegmentDecorator {

    protected static final String MESSAGE_BUS_ID = "message_bus.id";

    @Override
    public String getOperationName(Exchange exchange, Endpoint endpoint) {
        return getDestination(exchange, endpoint);
    }

    @Override
    public void pre(Entity segment, Exchange exchange, Endpoint endpoint) {
        super.pre(segment, exchange, endpoint);

        segment.putMetadata("message_bus.destination", getDestination(exchange, endpoint));

        String messageId = getMessageId(exchange);
        if (null != messageId) {
            segment.putMetadata(MESSAGE_BUS_ID, messageId);
        }
    }

    protected String getDestination(Exchange exchange, Endpoint endpoint) {
        return stripSchemeAndOptions(endpoint);
    }

    protected String getMessageId(Exchange exchange) {
        return null;
    }
}
