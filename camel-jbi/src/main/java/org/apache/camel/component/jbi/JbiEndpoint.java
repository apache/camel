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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * Represents an {@link Endpoint} for interacting with JBI
 *
 * @version $Revision$
 */
public class JbiEndpoint extends DefaultEndpoint<Exchange> {
    private Processor<Exchange> toJbiProcessor;
    private final CamelJbiComponent jbiComponent;

    public JbiEndpoint(CamelJbiComponent jbiComponent, String uri) {
        super(uri, jbiComponent.getCamelContext());
        this.jbiComponent = jbiComponent;
        toJbiProcessor = new ToJbiProcessor(jbiComponent.getBinding(), jbiComponent.getComponentContext(), uri);
    }

    /**
     * Sends a message into JBI
     */
    public void onExchange(Exchange exchange) {
        if (getInboundProcessor() != null) {
            getInboundProcessor().onExchange(exchange);
        } else {
            toJbiProcessor.onExchange(exchange);        }
    }

    @Override
    protected void doActivate() throws Exception {
        super.doActivate();

        // lets create and activate the endpoint in JBI
        jbiComponent.activateJbiEndpoint(this);
    }

    public JbiExchange createExchange() {
        return new JbiExchange(getContext(), getBinding());
    }

    public JbiBinding getBinding() {
        return jbiComponent.getBinding();
    }
}
