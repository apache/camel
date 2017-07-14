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

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.jaxrs.testbean.Customer;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.hamcrest.core.Is.is;

public class CxfRsSslProducerTest extends CamelSpringTestSupport {
    private static int port1 = CXFTestSupport.getSslPort();

    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    public int getPort1() {
        return port1;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {     
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/jaxrs/CxfRsSpringSslProducer.xml");
    }
    
    protected void setupDestinationURL(Message inMessage) {
        // do nothing here
    }
    
    @Test
    public void testCorrectTrustStore() {
        Exchange exchange = template.send("direct://trust", new MyProcessor());
     
        // get the response message 
        Customer response = (Customer) exchange.getOut().getBody();
        
        assertNotNull("The response should not be null ", response);
        assertEquals("Get a wrong customer id ", String.valueOf(response.getId()), "123");
        assertEquals("Get a wrong customer name", response.getName(), "John");
        assertEquals("Get a wrong response code", 200, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Get a wrong header value", "value", exchange.getOut().getHeader("key"));
    }

    @Test
    public void testNoTrustStore() {
        Exchange exchange = template.send("direct://noTrust", new MyProcessor());
        assertThat(exchange.isFailed(), is(true));
        Exception e = exchange.getException();
        assertThat(e.getCause().getClass().getCanonicalName(), is("javax.net.ssl.SSLHandshakeException"));
    }

    @Test
    public void testWrongTrustStore() {
        Exchange exchange = template.send("direct://wrongTrust", new MyProcessor());
        assertThat(exchange.isFailed(), is(true));
        Exception e = exchange.getException();
        assertThat(e.getCause().getClass().getCanonicalName(), is("javax.net.ssl.SSLHandshakeException"));
    }

    private class MyProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.setPattern(ExchangePattern.InOut);
            Message inMessage = exchange.getIn();
            setupDestinationURL(inMessage);
            // using the http central client API
            inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, Boolean.TRUE);
            // set the Http method
            inMessage.setHeader(Exchange.HTTP_METHOD, "GET");
            // set the relative path
            inMessage.setHeader(Exchange.HTTP_PATH, "/customerservice/customers/123");
            // Specify the response class , cxfrs will use InputStream as the response object type
            inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, Customer.class);
            // set a customer header
            inMessage.setHeader("key", "value");
            // since we use the Get method, so we don't need to set the message body
            inMessage.setBody(null);
        }
    }
}
