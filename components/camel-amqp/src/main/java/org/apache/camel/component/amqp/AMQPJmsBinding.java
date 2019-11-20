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
package org.apache.camel.component.amqp;

import java.util.Map;

import javax.jms.Message;

import org.apache.camel.Exchange;
import org.apache.camel.component.jms.JmsBinding;
import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsMessageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Strategy used to convert between a Camel {@link Exchange} and {@link org.apache.camel.component.jms.JmsMessage}
 * to and from a Qpid JMS {@link JmsMessage}
 */
public class AMQPJmsBinding extends JmsBinding {

    private static final Logger LOG = LoggerFactory.getLogger(AMQPJmsBinding.class);

    private boolean includeAmqpAnnotations;

    public AMQPJmsBinding(JmsEndpoint endpoint) {
        super(endpoint);
        if (endpoint.getConfiguration() instanceof AMQPConfiguration) {
            includeAmqpAnnotations = ((AMQPConfiguration) endpoint.getConfiguration()).isIncludeAmqpAnnotations();
        }
    }

    @Override
    public Map<String, Object> extractHeadersFromJms(Message jmsMessage, Exchange exchange) {
        Map<String, Object> headers = super.extractHeadersFromJms(jmsMessage, exchange);
        if (!includeAmqpAnnotations) {
            return headers;
        }

        AmqpJmsMessageFacade facade = getMessageFacade(jmsMessage);
        if (facade == null) {
            return headers;
        }

        // message annotations
        facade.filterTracingAnnotations((key, value) -> {
            LOG.trace("Extract message annotation: {} = {}", key, value);
            headers.put(AMQPConstants.JMS_AMQP_MA_PREFIX + key, value);
        });

        // delivery annotations
        // currently not possible to read due to the Facade API limitations
        // https://issues.apache.org/jira/browse/QPIDJMS-153

        return headers;
    }

    private AmqpJmsMessageFacade getMessageFacade(Message message) {
        if (message instanceof JmsMessage) {
            JmsMessage jmsMessage = (JmsMessage) message;
            if (jmsMessage.getFacade() instanceof AmqpJmsMessageFacade) {
                return (AmqpJmsMessageFacade) jmsMessage.getFacade();
            }
        }
        return null;
    }

}
