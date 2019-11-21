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
package org.apache.camel.component.activemq.converter;

import java.io.Serializable;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQObjectMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.jms.JmsBinding;

@Converter(generateLoader = true)
public class ActiveMQMessageConverter {
    private JmsBinding binding = new JmsBinding();

    /**
     * Converts the inbound message exchange to an ActiveMQ JMS message
     *
     * @return the ActiveMQ message
     */
    @Converter
    public ActiveMQMessage toMessage(Exchange exchange) throws JMSException {
        ActiveMQMessage message = createActiveMQMessage(exchange);
        getBinding().appendJmsProperties(message, exchange);
        return message;
    }

    /**
     * Allows a JMS {@link MessageListener} to be converted to a Camel
     * {@link Processor} so that we can provide better <a href="">Bean
     * Integration</a> so that we can use any JMS MessageListener in in Camel as
     * a bean
     * 
     * @param listener the JMS message listener
     * @return a newly created Camel Processor which when invoked will invoke
     *         {@link MessageListener#onMessage(Message)}
     */
    @Converter
    public Processor toProcessor(final MessageListener listener) {
        return new Processor() {
            public void process(Exchange exchange) throws Exception {
                Message message = toMessage(exchange);
                listener.onMessage(message);
            }

            @Override
            public String toString() {
                return "Processor of MessageListener: " + listener;
            }
        };
    }

    private static ActiveMQMessage createActiveMQMessage(Exchange exchange) throws JMSException {
        Object body = exchange.getIn().getBody();
        if (body instanceof String) {
            ActiveMQTextMessage answer = new ActiveMQTextMessage();
            answer.setText((String)body);
            return answer;
        } else if (body instanceof Serializable) {
            ActiveMQObjectMessage answer = new ActiveMQObjectMessage();
            answer.setObject((Serializable)body);
            return answer;
        } else {
            return new ActiveMQMessage();
        }

    }

    // Properties
    // -------------------------------------------------------------------------
    public JmsBinding getBinding() {
        return binding;
    }

    public void setBinding(JmsBinding binding) {
        this.binding = binding;
    }
}
