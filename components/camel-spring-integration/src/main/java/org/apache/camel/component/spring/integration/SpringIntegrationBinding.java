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
package org.apache.camel.component.spring.integration;

import java.util.Map;

import org.apache.camel.Exchange;
import org.springframework.messaging.support.GenericMessage;

/**
 * The helper class for Mapping between the Spring Integration message and the Camel Message.
 *
 * @version 
 */
public final class SpringIntegrationBinding {

    private SpringIntegrationBinding() {
        // Helper class
    }

    public static org.springframework.messaging.Message<?> createSpringIntegrationMessage(Exchange exchange) {
        return createSpringIntegrationMessage(exchange, exchange.getIn().getHeaders());
    }

    public static org.springframework.messaging.Message<?> createSpringIntegrationMessage(Exchange exchange, Map<String, Object> headers) {
        org.apache.camel.Message message = exchange.getIn();
        return new GenericMessage<Object>(message.getBody(), headers);
    }

    public static org.springframework.messaging.Message<?> storeToSpringIntegrationMessage(org.apache.camel.Message message) {
        return new GenericMessage<Object>(message.getBody(), message.getHeaders());
    }

    public static void storeToCamelMessage(org.springframework.messaging.Message<?> siMessage, org.apache.camel.Message cMessage) {
        cMessage.setBody(siMessage.getPayload());
        cMessage.setHeaders(siMessage.getHeaders());
    }

}
