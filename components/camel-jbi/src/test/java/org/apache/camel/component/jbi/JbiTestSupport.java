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
import org.apache.camel.Message;
import org.apache.camel.TestSupport;
import org.apache.camel.Processor;
import org.apache.camel.util.ProducerCache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.servicemix.jbi.container.ActivationSpec;
import org.apache.servicemix.jbi.container.SpringJBIContainer;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @version $Revision$
 */
public abstract class JbiTestSupport extends TestSupport {
    protected Exchange receivedExchange;
    protected CamelContext camelContext = new DefaultCamelContext();
    protected SpringJBIContainer jbiContainer = new SpringJBIContainer();
    protected CountDownLatch latch = new CountDownLatch(1);
    protected Endpoint<Exchange> endpoint;
    protected String startEndpointUri = "jbi:endpoint:serviceNamespace:serviceA:endpointA";
    protected ProducerCache<Exchange> client = new ProducerCache<Exchange>();

    /**
     * Sends an exchange to the endpoint
     */
    protected void sendExchange(final Object expectedBody) {
        client.send(endpoint, new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(expectedBody);
                in.setHeader("cheese", 123);
            }
        });
    }

    protected Object assertReceivedValidExchange(Class type) throws Exception {
        // lets wait on the message being received
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Did not receive the message!", received);

        assertNotNull(receivedExchange);
        Message receivedMessage = receivedExchange.getIn();

        assertEquals("cheese header", 123, receivedMessage.getHeader("cheese"));
        Object body = receivedMessage.getBody();
        log.debug("Received body: " + body);
        return body;
    }

    @Override
    protected void setUp() throws Exception {
        jbiContainer.setEmbedded(true);

        CamelJbiComponent component = new CamelJbiComponent();

        List<ActivationSpec> activationSpecList = new ArrayList<ActivationSpec>();

        // lets add the Camel endpoint
        ActivationSpec activationSpec = new ActivationSpec();
        activationSpec.setId("camel");
        activationSpec.setService(new QName("camel", "camel"));
        activationSpec.setEndpoint("camelEndpoint");
        activationSpec.setComponent(component);
        activationSpecList.add(activationSpec);

        appendJbiActivationSpecs(activationSpecList);

        ActivationSpec[] activationSpecs = activationSpecList.toArray(new ActivationSpec[activationSpecList.size()]);
        jbiContainer.setActivationSpecs(activationSpecs);
        jbiContainer.afterPropertiesSet();

        // lets configure some componnets
        camelContext.addComponent("jbi", component);

        // lets add some routes
        camelContext.addRoutes(createRoutes());
        endpoint = camelContext.getEndpoint(startEndpointUri);
        assertNotNull("No endpoint found!", endpoint);

        camelContext.start();
    }

    @Override
    protected void tearDown() throws Exception {
        client.stop();
        camelContext.stop();
        super.tearDown();
    }

    protected abstract void appendJbiActivationSpecs(List<ActivationSpec> activationSpecList);

    protected abstract RouteBuilder createRoutes();
}
