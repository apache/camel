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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.servicemix.jbi.resolver.URIResolver;

import javax.jbi.component.ComponentContext;
import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessageExchangeFactory;
import javax.jbi.messaging.MessagingException;

/**
 * A @{link Processor} which takes a Camel {@link Exchange} and invokes it into JBI using the straight JBI API
 *
 * @version $Revision$
 */
public class ToJbiProcessor implements Processor {
    private JbiBinding binding;
    private ComponentContext componentContext;
    private String destinationUri;

    public ToJbiProcessor(JbiBinding binding, ComponentContext componentContext, String destinationUri) {
        this.binding = binding;
        this.componentContext = componentContext;
        this.destinationUri = destinationUri;
    }

    public void process(Exchange exchange) {
        try {
            DeliveryChannel deliveryChannel = componentContext.getDeliveryChannel();
            MessageExchangeFactory exchangeFactory = deliveryChannel.createExchangeFactory();
            MessageExchange messageExchange = binding.makeJbiMessageExchange(exchange, exchangeFactory);

            URIResolver.configureExchange(messageExchange, componentContext, destinationUri);
            deliveryChannel.sendSync(messageExchange);
        }
        catch (MessagingException e) {
            throw new JbiException(e);
        }
    }
}
