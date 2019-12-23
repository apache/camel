/*
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
package org.apache.camel.component.cxf.jaxrs;

import javax.ws.rs.HttpMethod;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.jaxrs.testbean.Customer;
import org.apache.camel.component.cxf.jaxrs.testbean.CustomerService;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.AbstractJAXRSFactoryBean;
import org.apache.cxf.jaxrs.client.Client;
import org.apache.cxf.message.MessageContentsList;
import org.junit.Test;

public class CxfRsProducerEndpointConfigurerTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                CxfRsEndpoint endpoint = new CxfRsEndpoint();
                endpoint.setAddress("http://localhost:8000");
                endpoint.setCamelContext(context);
                endpoint.setResourceClasses(CustomerService.class);
                endpoint.setEndpointUriIfNotSpecified("cxfrs:simple");
                endpoint.setCxfRsConfigurer(new MyCxfRsConfigurer());

                from("direct:start")
                        .to(endpoint)
                        .to("mock:end");

                from("jetty:http://localhost:8000?matchOnUriPrefix=true")
                        .to("mock:result")
                        .process(exchange -> exchange.getIn().setBody(new Customer()));
            }
        };
    }

    @Test
    public void testCxfRsEndpoinConfigurerProxyApi() throws InterruptedException {
        template.send("direct:start", exchange -> {
            exchange.setPattern(ExchangePattern.InOut);
            Message inMessage = exchange.getIn();
            inMessage.setHeader(CxfConstants.OPERATION_NAME, "getCustomer");
            inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, Boolean.FALSE);
            MessageContentsList messageContentsList = new MessageContentsList();
            messageContentsList.add("1");
            inMessage.setBody(messageContentsList);
        });
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", "bar");
        getMockEndpoint("mock:end").expectedMessageCount(1);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCxfRsEndpointConfigurerHttpApi() throws InterruptedException {
        template.send("direct:start", exchange -> {
            exchange.setPattern(ExchangePattern.InOut);
            Message inMessage = exchange.getIn();
            inMessage.setHeader(Exchange.HTTP_PATH, "/customerservice/customers/1");
            inMessage.setHeader(Exchange.HTTP_METHOD, HttpMethod.GET);
            inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, Boolean.TRUE);
            inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, Customer.class);
        });
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", "bar");
        getMockEndpoint("mock:end").expectedMessageCount(1);
        assertMockEndpointsSatisfied();
    }

    public static class MyCxfRsConfigurer implements CxfRsConfigurer {

        @Override
        public void configure(AbstractJAXRSFactoryBean factoryBean) {
            // setup the wrong address here, it should be override from the address
            factoryBean.setAddress("xxxx");
        }

        @Override
        public void configureClient(Client client) {
            client.header("foo", "bar");
        }

        @Override
        public void configureServer(Server server) {
        }
    }

}
