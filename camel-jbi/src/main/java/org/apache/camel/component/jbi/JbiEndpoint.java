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
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * Represents an {@link Endpoint} for interacting with JBI
 *
 * @version $Revision$
 */
public class JbiEndpoint extends DefaultEndpoint<JbiExchange> {
    private JbiBinding binding;

    protected JbiEndpoint(String endpointUri, CamelContext container) {
        super(endpointUri, container);
    }

    public void onExchange(JbiExchange exchange) {
        // TODO
        // lets create a JBI MessageExchange and dispatch into JBI...
    }

    @Override
    protected void doActivate() throws Exception {
        super.doActivate();

        Processor<JbiExchange> processor = getInboundProcessor();

        // lets now wire up the processor to the JBI stuff...
    }

    public JbiExchange createExchange() {
        return new JbiExchange(getContext(), getBinding());
    }

    public JbiBinding getBinding() {
        if (binding == null) {
            binding = new JbiBinding();
        }
        return binding;
    }

    /**
     * Sets the binding on how Camel messages get mapped to JBI
     *
     * @param binding the new binding to use
     */
    public void setBinding(JbiBinding binding) {
        this.binding = binding;
    }
}
