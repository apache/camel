/**
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
package org.apache.camel.component.ref;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;

public class RefComponentTest extends ContextTestSupport {

    private void bindToRegistry(JndiRegistry jndi) throws Exception {
        Component comp = new DirectComponent();
        comp.setCamelContext(context);

        Endpoint slow = comp.createEndpoint("direct:somename");
        Consumer consumer = slow.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                template.send("mock:result", exchange);
            }
        });
        consumer.start();

        // bind our endpoint to the registry for ref to lookup
        jndi.bind("foo", slow);
    }

    public void testRef() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        PropertyPlaceholderDelegateRegistry delegate = (PropertyPlaceholderDelegateRegistry) context.getRegistry();
        JndiRegistry jndi = (JndiRegistry) delegate.getRegistry();
        bindToRegistry(jndi);

        template.sendBody("ref:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

}
