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
package org.apache.camel.component.bean;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version 
 */
public class BeanChoseMethodWithMatchingTypeAndSkipSettersTest extends ContextTestSupport {

    private OrderServiceBean service = new OrderServiceBean();

    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/file/order");
        super.setUp();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("orderService", service);
        return jndi;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        service.setConverter(context.getTypeConverter());
        return context;
    }

    public void testSendCSVFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:queue:order");
        mock.expectedBodiesReceived("66554,123,456");

        template.sendBodyAndHeader("file://target/file/order", "123,456", Exchange.FILE_NAME, "66554.csv");

        assertMockEndpointsSatisfied();
    }

    public void testSendXMLData() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:queue:order");
        mock.expectedBodiesReceived("77889,667,457");

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" 
                + "<order id=\"77889\">" 
                +    "<customer id=\"667\"/>" 
                +    "<confirm>457</confirm>" 
                + "</order>";
        template.sendBody("seda:xml", xml);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/file/order?initialDelay=0&delay=10", "seda:xml")
                    .bean("orderService")
                    .to("mock:queue:order");
            }
        };
    }

}
