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
package org.apache.camel.component.cxf;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;

/**
 * The endpoint in the service engine
 *
 * @version $Revision$
 */
public class CxfEndpoint extends DefaultEndpoint<CxfExchange> {
    private CxfBinding binding;

    protected CxfEndpoint(String uri, CamelContext camelContext) {
        super(uri, camelContext);
    }

    public Producer<CxfExchange> createProducer() throws Exception {
        return startService(new DefaultProducer<CxfExchange>(this) {
            public void onExchange(CxfExchange exchange) {
                // TODO send into CXF
            }
        });
    }

    public Consumer<CxfExchange> createConsumer(Processor<CxfExchange> processor) throws Exception {
        return startService(new DefaultConsumer<CxfExchange>(this, processor) {
        });
    }

    public CxfExchange createExchange() {
        return new CxfExchange(getContext(), getBinding());
    }

    public CxfBinding getBinding() {
        if (binding == null) {
            binding = new CxfBinding();
        }
        return binding;
    }

    public void setBinding(CxfBinding binding) {
        this.binding = binding;
    }

    @Override
    protected void doActivate() throws Exception {
        super.doActivate();

        // TODO process any inbound messages from CXF

        Processor<CxfExchange> processor = getInboundProcessor();
        if (processor != null) {

        }
    }
}
