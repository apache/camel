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

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.jaxrs.testbean.Customer;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 */
public class CxfRsProducerClientFactoryCacheTest extends CamelSpringTestSupport {
    private static int port1 = CXFTestSupport.getPort1();

    @Test
    public void testGetCostumerWithHttpCentralClientAPI() throws Exception {
        doRunTest(template, getPort1());
    }

    private void doRunTest(ProducerTemplate template, final int clientPort) {
        Exchange exchange = template.send("direct://http", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                Message inMessage = exchange.getIn();
                inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, Boolean.TRUE);
                inMessage.setHeader(Exchange.HTTP_METHOD, "GET");
                inMessage.setHeader(Exchange.HTTP_PATH, "/customerservice/customers/123");
                inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, Customer.class);
                inMessage.setHeader("clientPort", clientPort);
                inMessage.setBody(null);
            }
        });

        // get the response message
        Customer response = (Customer) exchange.getMessage().getBody();

        assertNotNull(response, "The response should not be null");
        assertEquals("123", String.valueOf(response.getId()), "Get a wrong customer id");
        assertEquals("John", response.getName(), "Get a wrong customer name");
        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE), "Get a wrong response code");
    }

    public int getPort1() {
        return port1;
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/component/cxf/jaxrs/CxfRsProducerClientFactoryCacheTest1.xml");
    }

}
