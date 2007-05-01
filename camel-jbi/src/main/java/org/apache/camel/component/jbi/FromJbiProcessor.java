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
package org.apache.camel.component.jbi;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.servicemix.MessageExchangeListener;

import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;

/**
 * @version $Revision$
 */
public class FromJbiProcessor implements MessageExchangeListener {
    private CamelContext context;
    private JbiBinding binding;
    private Processor processor;

    public FromJbiProcessor(CamelContext context, JbiBinding binding, Processor processor) {
        this.context = context;
        this.binding = binding;
        this.processor = processor;
    }

    public void onMessageExchange(MessageExchange messageExchange) throws MessagingException {
        try {
			JbiExchange exchange = new JbiExchange(context, binding, messageExchange);
			processor.process(exchange);
		} catch (Exception e) {
			throw new MessagingException(e);
		}
    }
}
