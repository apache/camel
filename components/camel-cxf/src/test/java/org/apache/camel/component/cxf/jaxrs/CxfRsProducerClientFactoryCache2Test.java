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
package org.apache.camel.component.cxf.jaxrs;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.jaxrs.testbean.Customer;
import org.apache.camel.spring.SpringCamelContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 *
 */
public class CxfRsProducerClientFactoryCache2Test extends Assert {
    private static int port2 = CXFTestSupport.getPort("mySecurePort");

    private CamelContext context2;
    private ProducerTemplate template2;
    private AbstractApplicationContext applicationContext;

    @Before
    public void setUp() throws Exception {
        applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/jaxrs/CxfRsProducerClientFactoryCacheTest2.xml");
        context2 = SpringCamelContext.springCamelContext(applicationContext, false);
        context2.start();

        template2 = context2.createProducerTemplate();
        template2.start();
    }
    
    @After
    public void tearDown() throws Exception {
        if (context2 != null) {
            context2.stop();
            template2.stop();
        }
        // need to shutdown the application context to shutdown the bus
        if (applicationContext != null) {
            applicationContext.close();
        }
    }
    
    @Test
    public void testGetCostumerWithHttpCentralClientAPI() throws Exception {
        doRunTest(template2, getPort2());
    }

    private void doRunTest(ProducerTemplate template, final int clientPort) {
        // use request as we want InOut
        Exchange exchange = template.request("direct://http", new Processor() {
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
        Customer response = (Customer) exchange.getOut().getBody();
        
        assertNotNull("The response should not be null ", response);
        assertEquals("Get a wrong customer id ", String.valueOf(response.getId()), "123");
        assertEquals("Get a wrong customer name", response.getName(), "John");
        assertEquals("Get a wrong response code", 200, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    public int getPort2() {
        return port2;
    }
}
