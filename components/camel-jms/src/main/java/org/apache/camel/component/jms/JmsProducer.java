/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jms;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.MessageCreator;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

/**
 * @version $Revision$
 */
public class JmsProducer extends DefaultProducer {
    private static final transient Log log = LogFactory.getLog(JmsProducer.class);
    private final JmsEndpoint endpoint;
    private final JmsOperations template;

    public JmsProducer(JmsEndpoint endpoint, JmsOperations template) {
        super(endpoint);
        this.endpoint = endpoint;
        this.template = template;
    }

    public void process(final Exchange exchange) {
        template.send(endpoint.getDestination(), new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                Message message = endpoint.getBinding().makeJmsMessage(exchange, session);
                if (log.isDebugEnabled()) {
                    log.debug(endpoint + " sending JMS message: " + message);
                }
                return message;
            }
        });
    }

    public JmsOperations getTemplate() {
        return template;
    }
}
