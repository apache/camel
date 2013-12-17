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
import java.net.URL;

import javax.servlet.ServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.NoErrorHandlerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.jaxrs.testbean.Customer;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Test;


public class CxfRsConsumerTest extends CamelTestSupport {
    private static final String PUT_REQUEST = "<Customer><name>Mary</name><id>123</id></Customer>";
    private static final String CXT = CXFTestSupport.getPort1() + "/CxfRsConsumerTest";
    // START SNIPPET: example
    private static final String CXF_RS_ENDPOINT_URI = "cxfrs://http://localhost:" + CXT + "/rest?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerServiceResource";
    
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
                            String id = inMessage.getBody(String.class);
                            if ("/customerservice/customers/126".equals(path)) {                            
                                Customer customer = new Customer();
                                customer.setId(Long.parseLong(id));
                                customer.setName("Willem");
                                // We just put the response Object into the out message body
                                exchange.getOut().setBody(customer);
                            } else {
                                if ("/customerservice/customers/400".equals(path)) {
                                    // We return the remote client IP address this time
                                    org.apache.cxf.message.Message cxfMessage = inMessage.getHeader(CxfConstants.CAMEL_CXF_MESSAGE, org.apache.cxf.message.Message.class);
                                    ServletRequest request = (ServletRequest) cxfMessage.get("HTTP.REQUEST");
                                    String remoteAddress = request.getRemoteAddr();
                                    Response r = Response.status(200).entity("The remoteAddress is " + remoteAddress).build();
                                    exchange.getOut().setBody(r);
                                    return;
                                }
                                if ("/customerservice/customers/123".equals(path)) {
                                    // send a customer response back
                                    Response r = Response.status(200).entity("customer response back!").build();
                                    exchange.getOut().setBody(r);
                                    return;
                                }
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
    
    private void invokeGetCustomer(String uri, String expect) throws Exception {
        HttpGet get = new HttpGet(uri);
        get.addHeader("Accept" , "application/json");
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();

        try {
            HttpResponse response = httpclient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals(expect,
                         EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.close();
        }
    }
    
    @Test
    public void testGetCustomer() throws Exception {
        invokeGetCustomer("http://localhost:" + CXT + "/rest/customerservice/customers/126",
                          "{\"Customer\":{\"id\":126,\"name\":\"Willem\"}}");
        invokeGetCustomer("http://localhost:" + CXT + "/rest/customerservice/customers/123",
                          "customer response back!");
        invokeGetCustomer("http://localhost:" + CXT + "/rest/customerservice/customers/400",
            "The remoteAddress is 127.0.0.1");
        
    }
    
    
    
    @Test
    public void testGetWrongCustomer() throws Exception {
        URL url = new URL("http://localhost:" + CXT + "/rest/customerservice/customers/456");
        try {
            url.openStream();
            fail("Expect to get exception here");
        } catch (FileNotFoundException exception) {
            // do nothing here
        }
        url = new URL("http://localhost:" + CXT + "/rest/customerservice/customers/256");
        try {
            url.openStream();
            fail("Expect to get exception here");
        } catch (IOException exception) {
            // expect the Internal error exception
        }
        
    }
    
    @Test
    public void testPutConsumer() throws Exception {
        HttpPut put = new HttpPut("http://localhost:" + CXT + "/rest/customerservice/customers");
        StringEntity entity = new StringEntity(PUT_REQUEST, "ISO-8859-1");
        entity.setContentType("text/xml; charset=ISO-8859-1");
        put.addHeader("test", "header1;header2");
        put.setEntity(entity);
        CloseableHttpClient httpclient = HttpClientBuilder.create().build();

        try {
            HttpResponse response = httpclient.execute(put);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("", EntityUtils.toString(response.getEntity()));
        } finally {
            httpclient.close();
        }
    }
        

}
