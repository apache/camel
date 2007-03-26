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
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.servicemix.client.Destination;
import org.apache.servicemix.client.ServiceMixClient;

import javax.jbi.messaging.MessagingException;
import javax.jbi.component.ComponentContext;

/**
 * Represents an {@link Endpoint} for interacting with JBI
 *
 * @version $Revision$
 */
public class JbiEndpoint extends DefaultEndpoint<Exchange> {
    private final JbiBinding binding;
    private ToJbiProcessor toJbiProcessor;

    public JbiEndpoint(String endpointUri, CamelContext container, ComponentContext componentContext, JbiBinding binding) {
        super(endpointUri, container);
        this.binding = binding;
        toJbiProcessor = new ToJbiProcessor(binding, componentContext, endpointUri);
    }

    /**
     * Sends a message into JBI
     */
    public void onExchange(Exchange exchange) {
        toJbiProcessor.onExchange(exchange);
    }

    @Override
    protected void doActivate() throws Exception {
        super.doActivate();

        // TODO once the inbound is activated we need to register a JBI endpoint
        
    }

    public JbiExchange createExchange() {
        return new JbiExchange(getContext(), getBinding());
    }

    public JbiBinding getBinding() {
        return binding;
    }
}
