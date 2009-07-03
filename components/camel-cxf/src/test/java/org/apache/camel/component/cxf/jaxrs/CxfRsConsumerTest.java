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

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.CxfConstants;
import org.apache.camel.component.cxf.jaxrs.testbean.Customer;
import org.apache.camel.component.cxf.util.CxfUtils;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;


public class CxfRsConsumerTest extends CamelTestSupport {
    private static final String CXF_RS_ENDPOINT_URI = "cxfrs://http://localhost:9000?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService";
    
    // START SNIPPET: example
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from(CXF_RS_ENDPOINT_URI).process(new Processor() {

                    public void process(Exchange exchange) throws Exception {
                        Message inMessage = exchange.getIn();                        
                        // Get the operation name from in message
                        String operationName = inMessage.getHeader(CxfConstants.OPERATION_NAME, String.class);
                        // The parameter of the invocation is stored in the body of in message
                        String id = (String) inMessage.getBody(Object[].class)[0];
                        if ("getCustomer".equals(operationName)) {
                            String httpMethod = inMessage.getHeader(Exchange.HTTP_METHOD, String.class);
                            assertEquals("Get a wrong http method", "GET", httpMethod);
                            String uri = inMessage.getHeader(Exchange.HTTP_URI, String.class);                            
                            assertEquals("Get a wrong http uri", "/customerservice/customers/126", uri);
                            Customer customer = new Customer();
                            customer.setId(Long.parseLong(id));
                            customer.setName("Willem");
                            // We just put the response Object into the out message body
                            exchange.getOut().setBody(customer);
                        }
                    }
                    
                });
            }
        };
    }
    // END SNIPPET: example
    
    @Test
    public void testGetCustomer() throws Exception {
        URL url = new URL("http://localhost:9000/customerservice/customers/126");

        InputStream in = url.openStream();
        assertEquals("{\"Customer\":{\"id\":126,\"name\":\"Willem\"}}", CxfUtils.getStringFromInputStream(in));
       
    }
        

}
