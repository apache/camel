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
package org.apache.camel.itest.greeter;

import java.util.ArrayList;
import java.util.List;

import javax.xml.ws.Endpoint;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.cxf.CxfConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;

@ContextConfiguration
public class CamelGreeterTest extends AbstractJUnit38SpringContextTests {
    private static final transient Log LOG = LogFactory.getLog(CamelGreeterTest.class);

    @Autowired
    protected CamelContext camelContext;

    @EndpointInject(uri = "mock:resultEndpoint")
    protected MockEndpoint resultEndpoint;

    private Endpoint endpoint;


    protected void setUp() throws Exception {
        // Start the Greeter Server
        Object implementor = new GreeterImpl();
        String address = "http://localhost:9000/SoapContext/SoapPort";
        endpoint = Endpoint.publish(address, implementor);
        LOG.info("The WS endpoint is published! ");

    }

    protected void tearDown() throws Exception {
        // Shutdown the Greeter Server
        if (endpoint != null) {
            endpoint.stop();
        }
    }



    public void testMocksAreValid() throws Exception {
        assertNotNull(camelContext);
        assertNotNull(resultEndpoint);

        ProducerTemplate<Exchange> template = camelContext.createProducerTemplate();
        template.sendBodyAndHeader("jms:requestQueue", "Willem", CxfConstants.OPERATION_NAME, "greetMe");

        // Sleep a while and wait for the message whole processing
        Thread.sleep(4000);

        MockEndpoint.assertIsSatisfied(camelContext);
        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        assertEquals("Should get one message", list.size(), 1);
        for (Exchange exchange : list) {
            String result = (String) exchange.getIn().getBody();
            assertEquals("Get the wrong result ", result, "Hello Willem");
        }


    }

}
