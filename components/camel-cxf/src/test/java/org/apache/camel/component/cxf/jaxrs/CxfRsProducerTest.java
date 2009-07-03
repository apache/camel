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
import org.apache.camel.component.cxf.CxfConstants;
import org.apache.camel.component.cxf.jaxrs.testbean.Customer;
import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfRsProducerTest extends CamelSpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {        
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/jaxrs/CxfRsSpringProducer.xml");
    }
    
    @Test
    public void testGetConstumerWithClientProxyAPI() {
        // START SNIPPET: example
        Exchange exchange = template.send("direct:start", new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                Message inMessage = exchange.getIn();
                // set the operation name 
                inMessage.setHeader(CxfConstants.OPERATION_NAME, "getCustomer");
                // using the proxy client API
                inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, Boolean.FALSE);
                // set the parameters , if you just have one parameter 
                // camel will put this object into an Object[] itself
                inMessage.setBody("123");
            }
            
        });
     
        // get the response message 
        Customer response = (Customer) exchange.getOut().getBody();
        
        assertNotNull("The response should not be null ", response);
        assertEquals("Get a wrong customer id ", String.valueOf(response.getId()), "123");
        assertEquals("Get a wrong customer name", response.getName(), "John");
        // END SNIPPET: example        
    }
    
    @Test
    public void testGetConstumerWithHttpCentralClientAPI() {
     // START SNIPPET: example-http
        Exchange exchange = template.send("direct:start", new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                Message inMessage = exchange.getIn();
                // using the http central client API
                inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, Boolean.TRUE);
                // set the Http method
                inMessage.setHeader(Exchange.HTTP_METHOD, "GET");
                // set the relative path
                inMessage.setHeader(Exchange.HTTP_PATH, "/customerservice/customers/123");                
                // Specify the response class , cxfrs will use InputStream as the response object type 
                inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, Customer.class);
                // since we use the Get method, so we don't need to set the message body
                inMessage.setBody(null);                
            }
            
        });
     
        // get the response message 
        Customer response = (Customer) exchange.getOut().getBody();
        
        assertNotNull("The response should not be null ", response);
        assertEquals("Get a wrong customer id ", String.valueOf(response.getId()), "123");
        assertEquals("Get a wrong customer name", response.getName(), "John");
        // END SNIPPET: example-http 
    }
    

}
