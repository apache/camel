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
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;

import javax.jms.Message;
import javax.jms.MessageListener;

/**
 * Represents a JMS {@link MessageListener} which can be used directly with any JMS template
 * or derived from to create an MDB for processing messages using a {@link Processor}
 *
 * @version $Revision:520964 $
 */
public class MessageListenerProcessor implements MessageListener {
    private final JmsEndpoint endpoint;
    private final Processor processor;

    public MessageListenerProcessor(JmsEndpoint endpoint, Processor processor) {
        this.endpoint = endpoint;
        this.processor = processor;
    }

    public void onMessage(Message message) {
        try {
			Exchange exchange = endpoint.createExchange(message);
			processor.process(exchange);
		} catch (Exception e) {
			throw new RuntimeCamelException(e);
		}
    }
}
