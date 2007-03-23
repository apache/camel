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
import org.apache.servicemix.client.ServiceMixClient;
import org.apache.servicemix.client.Destination;

import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;

/**
 * A @{link Processor} which takes a Camel {@link Exchange} and invokes it into JBI using the @{link ServiceMixClient}
 *
 * @version $Revision$
 */
public class ToJbiProcessor2 implements Processor<Exchange> {
    private JbiBinding binding;
    private ServiceMixClient client;
    private Destination destination;

    public ToJbiProcessor2(JbiBinding binding, ServiceMixClient client, Destination destination) {
        this.binding = binding;
        this.client = client;
        this.destination = destination;
    }

    public void onExchange(Exchange exchange) {
        try {
            MessageExchange messageExchange = binding.makeJbiMessageExchange(exchange, destination);
            client.sendSync(messageExchange);
        }
        catch (MessagingException e) {
            throw new JbiException(e);
        }
    }
}
