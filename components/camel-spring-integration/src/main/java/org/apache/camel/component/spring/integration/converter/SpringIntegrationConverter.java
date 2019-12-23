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
package org.apache.camel.component.spring.integration.converter;

import org.apache.camel.CamelContext;
import org.apache.camel.Converter;
import org.apache.camel.Endpoint;
import org.apache.camel.component.spring.integration.SpringIntegrationEndpoint;
import org.apache.camel.component.spring.integration.SpringIntegrationMessage;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

/**
 * The <a href="http://camel.apache.org/type-converter.html">Type Converters</a>
 * for turning the Spring Integration types into Camel native type.
 */
@Converter(generateLoader = true)
public final class SpringIntegrationConverter {

    private SpringIntegrationConverter() {
        // Helper class
    }

    @Converter
    public static Endpoint toEndpoint(final MessageChannel channel) throws Exception {
        Endpoint answer = new SpringIntegrationEndpoint("spring-integration://" + channel.toString(), channel.toString(), null);
        return answer;
    }

    @Converter
    public static org.springframework.messaging.Message<?> toSpringMessage(final org.apache.camel.Message camelMessage) throws Exception {
        if (camelMessage instanceof SpringIntegrationMessage) {
            SpringIntegrationMessage siMessage = (SpringIntegrationMessage)camelMessage;
            org.springframework.messaging.Message<?> message =  siMessage.getMessage();
            if (message != null) {
                return message;
            }
        }

        // Create a new spring message and copy the attributes and body from the camel message
        MessageHeaders messageHeaders = new MessageHeaders(camelMessage.getHeaders());
        return new GenericMessage<>(camelMessage.getBody(), messageHeaders);
    }

    @Converter
    public static org.apache.camel.Message toCamelMessage(final org.springframework.messaging.Message<?> springMessage) throws Exception {
        return new SpringIntegrationMessage((CamelContext) null, springMessage);
    }

}
