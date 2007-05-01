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

import javax.jms.Message;
import javax.jms.MessageListener;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A JMS {@link MessageListener} which can be used to delegate processing to a Camel endpoint.
 *
 * @version $Revision$
 */
public class EndpointMessageListener<E extends Exchange> implements MessageListener {
    private static final transient Log log = LogFactory.getLog(EndpointMessageListener.class);
    private Endpoint<E> endpoint;
    private Processor processor;
    private JmsBinding binding;

    public EndpointMessageListener(Endpoint<E> endpoint, Processor processor) {
        this.endpoint = endpoint;
        this.processor = processor;
    }

    public void onMessage(Message message) {
        try {
			
        	if (log.isDebugEnabled()) {
			    log.debug(endpoint + " receiving JMS message: " + message);
			}
			JmsExchange exchange = createExchange(message);
			processor.process((E) exchange);
			
		} catch (Exception e) {
			throw new RuntimeCamelException(e);
		}
    }

    public JmsExchange createExchange(Message message) {
        return new JmsExchange(endpoint.getContext(), getBinding(), message);
    }

    // Properties
    //-------------------------------------------------------------------------
    public JmsBinding getBinding() {
        if (binding == null) {
            binding = new JmsBinding();
        }
        return binding;
    }

    /**
     * Sets the binding used to convert from a Camel message to and from a JMS message
     *
     * @param binding the binding to use
     */
    public void setBinding(JmsBinding binding) {
        this.binding = binding;
    }
}
