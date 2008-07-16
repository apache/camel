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
package org.apache.camel.component.spring.integration.converter;

import java.util.Map;

import org.apache.camel.Converter;
import org.apache.camel.Endpoint;
import org.apache.camel.component.spring.integration.SpringIntegrationEndpoint;
import org.apache.camel.component.spring.integration.SpringIntegrationMessage;
import org.springframework.integration.channel.MessageChannel;
import org.springframework.integration.message.DefaultMessageHeader;
import org.springframework.integration.message.GenericMessage;

/**
 * The <a href="http://activemq.apache.org/camel/type-converter.html">Type Converters</a>
 * for turning the Spring Integration types into Camel native type.
 *
 * @version $Revision$
 */
@Converter
public final class SpringIntegrationConverter {

    private SpringIntegrationConverter() {
        // Helper class
    }

    /**
     * @param Spring Integration MessageChannel
     * @return an Camel Endpoint
     * @throws Exception
     */
    @Converter
    public static Endpoint toEndpoint(final MessageChannel channel) throws Exception {
        if (channel == null) {
            throw new IllegalArgumentException("The MessageChannel is null");
        }
        Endpoint answer = new SpringIntegrationEndpoint("URL", channel, null);
        // check the channel
        return answer;
    }


    @SuppressWarnings("unchecked")
    @Converter
    public static org.springframework.integration.message.Message toSpringMessage(final org.apache.camel.Message camelMessage) throws Exception {
        if (camelMessage instanceof SpringIntegrationMessage) {
            SpringIntegrationMessage siMessage = (SpringIntegrationMessage)camelMessage;
            org.springframework.integration.message.Message message =  siMessage.getMessage();
            if (message != null) {
                return message;
            }
        }

        // Create a new spring message and copy the attributes and body from the camel message
        DefaultMessageHeader messageHeader = new DefaultMessageHeader();
        Map<String, Object> headers = camelMessage.getHeaders();
        for (String key : headers.keySet()) {
            Object value = headers.get(key);
            messageHeader.setAttribute(key, value);
        }
        return new GenericMessage(camelMessage.getBody(), messageHeader);
    }

    @Converter
    public static org.apache.camel.Message toCamelMessage(final org.springframework.integration.message.Message springMessage) throws Exception {
        return new SpringIntegrationMessage(springMessage);
    }



}
