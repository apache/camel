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

import junit.framework.TestCase;
import org.apache.servicemix.jbi.container.JBIContainer;
import org.apache.servicemix.jbi.container.ActivationSpec;
import org.apache.servicemix.jbi.container.SpringJBIContainer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.*;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

import javax.xml.namespace.QName;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @version $Revision$
 */
public class JbiTest extends TestCase {
    private static final transient Log log = LogFactory.getLog(JbiTest.class);

    public void testCamelInvokingJbi() throws Exception {
        sendExchange("<foo bar='123'/>");
    }


    protected Exchange receivedExchange;
    protected CamelContext camelContext = new DefaultCamelContext();
    protected SpringJBIContainer jbiContainer = new SpringJBIContainer();
    protected CountDownLatch latch = new CountDownLatch(1);
    protected Endpoint<Exchange> endpoint;

    protected void sendExchange(Object expectedBody) {
        // now lets fire in a message
        Exchange exchange = endpoint.createExchange();
        Message in = exchange.getIn();
        in.setBody(expectedBody);
        in.setHeader("cheese", 123);
        endpoint.onExchange(exchange);
    }

    protected Object assertReceivedValidExchange(Class type) throws Exception {
        // lets wait on the message being received
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Did not recieve the message!", received);

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

        /*

        <bean class="org.apache.servicemix.jbi.container.ActivationSpec">
          <property name="id" value="receiver"/>
          <property name="service" ref="receiverServiceName"/>
          <property name="endpoint" value="receiver"/>
          <!-- no need to specify service on this POJO as it is hard coded -->
          <property name="component">
            <bean class="org.apache.servicemix.tck.ReceiverComponent"/>
          </property>
        </bean>

         */
        ActivationSpec activationSpec = new ActivationSpec();
        activationSpec.setId("camel");
        activationSpec.setService(new QName("camel"));
        activationSpec.setEndpoint("camelEndpoint");
        activationSpec.setComponent(component);

        //activationSpec.setComponentName("camel");

        jbiContainer.setActivationSpecs(new ActivationSpec[] {activationSpec});
        jbiContainer.afterPropertiesSet();

        //jbiContainer.activateComponent(component, "camel");

        // lets configure some componnets
        camelContext.addComponent("jbi", component);

        // lets add some routes
        camelContext.setRoutes(new RouteBuilder() {
            public void configure() {
                from("jbi:service:test:a").to("jbi:service:test:b");
                from("jbi:service:test:b").process(new Processor<Exchange>() {
                    public void onExchange(Exchange e) {
                        System.out.println("Received exchange: " + e.getIn());
                        receivedExchange = e;
                        latch.countDown();
                    }
                });
            }
        });
        endpoint = camelContext.resolveEndpoint("jbi:service:test:a");
        assertNotNull("No endpoint found!", endpoint);

        camelContext.activateEndpoints();
    }

    @Override
    protected void tearDown() throws Exception {
        camelContext.deactivateEndpoints();
    }
}
