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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.NoErrorHandlerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.CxfConstants;
import org.apache.camel.component.cxf.jaxrs.testbean.Customer;
import org.apache.camel.component.cxf.util.CxfUtils;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.Test;


public class CxfRsConsumerTest extends CamelTestSupport {
    private static final String PUT_REQUEST = "<Customer><name>Mary</name><id>123</id></Customer>";
    private static final String CXF_RS_ENDPOINT_URI = "cxfrs://http://localhost:9000/rest?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService";
    
    // START SNIPPET: example
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(new NoErrorHandlerBuilder());
                from(CXF_RS_ENDPOINT_URI).process(new Processor() {

                    public void process(Exchange exchange) throws Exception {
                        Message inMessage = exchange.getIn();                        
                        // Get the operation name from in message
                        String operationName = inMessage.getHeader(CxfConstants.OPERATION_NAME, String.class);
                        if ("getCustomer".equals(operationName)) {
                            String httpMethod = inMessage.getHeader(Exchange.HTTP_METHOD, String.class);
                            assertEquals("Get a wrong http method", "GET", httpMethod);
                            String path = inMessage.getHeader(Exchange.HTTP_PATH, String.class);
                            // The parameter of the invocation is stored in the body of in message
                            String id = (String) inMessage.getBody(String.class);
                            if ("/customerservice/customers/126".equals(path)) {                            
                                Customer customer = new Customer();
                                customer.setId(Long.parseLong(id));
                                customer.setName("Willem");
                                // We just put the response Object into the out message body
                                exchange.getOut().setBody(customer);
                            } else {
                                if ("/customerservice/customers/456".equals(path)) {
                                    Response r = Response.status(404).entity("Can't found the customer with uri " + path).build();
                                    throw new WebApplicationException(r);
                                } else {
                                    throw new RuntimeCamelException("Can't found the customer with uri " + path);
                                }
                            }
                        }
                        if ("updateCustomer".equals(operationName)) {
                            assertEquals("Get a wrong customer message header", "header1;header2", inMessage.getHeader("test"));
                            String httpMethod = inMessage.getHeader(Exchange.HTTP_METHOD, String.class);
                            assertEquals("Get a wrong http method", "PUT", httpMethod);
                            Customer customer = inMessage.getBody(Customer.class);
                            assertNotNull("The customer should not be null.", customer);
                            // Now you can do what you want on the customer object
                            assertEquals("Get a wrong customer name.", "Mary", customer.getName());
                            // set the response back
                            exchange.getOut().setBody(Response.ok().build());
                        }
                    }
                    
                });
            }
        };
    }
    // END SNIPPET: example
    
    @Test
    public void testGetCustomer() throws Exception {
        URL url = new URL("http://localhost:9000/rest/customerservice/customers/126");

        InputStream in = url.openStream();
        assertEquals("{\"Customer\":{\"id\":126,\"name\":\"Willem\"}}", CxfUtils.getStringFromInputStream(in));
       
    }
    
    @Test
    public void testGetWrongCustomer() throws Exception {
        URL url = new URL("http://localhost:9000/rest/customerservice/customers/456");
        try {
            url.openStream();
            fail("Expect to get exception here");
        } catch (FileNotFoundException exception) {
            // do nothing here
        }
        url = new URL("http://localhost:9000/rest/customerservice/customers/256");
        try {
            url.openStream();
            fail("Expect to get exception here");
        } catch (IOException exception) {
            // expect the Internal error exception
        }
        
    }
    
    @Test
    public void testPutConsumer() throws Exception {
        HttpPut put = new HttpPut("http://localhost:9000/rest/customerservice/customers");
        StringEntity entity = new StringEntity(PUT_REQUEST, "ISO-8859-1");
        entity.setContentType("text/xml; charset=ISO-8859-1");
        put.addHeader("test", "header1;header2");
        put.setEntity(entity);
        HttpClient httpclient = new DefaultHttpClient();

        try {
            HttpResponse response = httpclient.execute(put);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("", EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.getConnectionManager().shutdown();
        }
    }
        

}
