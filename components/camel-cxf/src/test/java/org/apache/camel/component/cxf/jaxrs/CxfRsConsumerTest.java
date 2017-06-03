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

import javax.servlet.ServletRequest;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

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
    private static final String CXF_RS_ENDPOINT_URI = 
            "cxfrs://http://localhost:" + CXT + "/rest?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerServiceResource";
    private static final String CXF_RS_ENDPOINT_URI2 = 
            "cxfrs://http://localhost:" + CXT + "/rest2?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService";
    private static final String CXF_RS_ENDPOINT_URI3 = 
            "cxfrs://http://localhost:" + CXT + "/rest3?"
            + "resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerServiceNoAnnotations&"
            + "modelRef=classpath:/org/apache/camel/component/cxf/jaxrs/CustomerServiceModel.xml";
    private static final String CXF_RS_ENDPOINT_URI4 = 
            "cxfrs://http://localhost:" + CXT + "/rest4?"
            + "modelRef=classpath:/org/apache/camel/component/cxf/jaxrs/CustomerServiceDefaultHandlerModel.xml";
    private static final String CXF_RS_ENDPOINT_URI5 = 
            "cxfrs://http://localhost:" + CXT + "/rest5?"
            + "propagateContexts=true&"
            + "modelRef=classpath:/org/apache/camel/component/cxf/jaxrs/CustomerServiceDefaultHandlerModel.xml";
    protected RouteBuilder createRouteBuilder() throws Exception {
        final Processor testProcessor = new TestProcessor();
        final Processor testProcessor2 = new TestProcessor2();
        final Processor testProcessor3 = new TestProcessor3();
        return new RouteBuilder() {
            public void configure() {
                errorHandler(new NoErrorHandlerBuilder());
                from(CXF_RS_ENDPOINT_URI).process(testProcessor);
                from(CXF_RS_ENDPOINT_URI2).process(testProcessor);
                from(CXF_RS_ENDPOINT_URI3).process(testProcessor);
                from(CXF_RS_ENDPOINT_URI4).process(testProcessor2);
                from(CXF_RS_ENDPOINT_URI5).process(testProcessor3);
            }
        };
    }
    // END SNIPPET: example
    
    private void invokeGetCustomer(String uri, String expect) throws Exception {
        HttpGet get = new HttpGet(uri);
        get.addHeader("Accept", "application/json");
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
    public void testGetCustomerInterface() throws Exception {
        doTestGetCustomer("rest");
    }
    @Test
    public void testGetCustomerImpl() throws Exception {
        doTestGetCustomer("rest2");
    }
    @Test
    public void testGetCustomerInterfaceAndModel() throws Exception {
        doTestGetCustomer("rest3");
    }
    @Test
    public void testGetCustomerDefaultHandlerAndModel() throws Exception {
        doTestGetCustomer("rest4");
    }
    @Test
    public void testEchoCustomerDefaultHandlerAndModel() throws Exception {
        WebTarget target = 
            ClientBuilder.newClient().target("http://localhost:" + CXT + "/" + "rest4" + "/customerservice/customers");
        Customer c = 
            target.request(MediaType.APPLICATION_JSON).post(Entity.json(new Customer(333, "Barry")), Customer.class);
        assertEquals(333L, c.getId());
        assertEquals("Barry", c.getName());
    }
    @Test
    public void testGetCustomerDefaultHandlerAndModelAndContexts() throws Exception {
        doTestGetCustomer("rest5");
    }
    private void doTestGetCustomer(String contextUri) throws Exception {
        invokeGetCustomer("http://localhost:" + CXT + "/" + contextUri + "/customerservice/customers/126",
                          "{\"Customer\":{\"id\":126,\"name\":\"Willem\"}}");
        invokeGetCustomer("http://localhost:" + CXT + "/" + contextUri + "/customerservice/customers/123",
                          "customer response back!");
        invokeGetCustomer("http://localhost:" + CXT + "/" + contextUri + "/customerservice/customers/400",
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
        
        url = new URL("http://localhost:" + CXT + "/rest/customerservice/customers/234");
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
     
    private abstract static class AbstractTestProcessor implements Processor {
        public void processGetCustomer(Exchange exchange) throws Exception {
            Message inMessage = exchange.getIn();                        
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
                    // Just make sure the request object is not null
                    assertNotNull("The request object should not be null", request);
                    Response r = Response.status(200).entity("The remoteAddress is 127.0.0.1").build();
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
                } else if ("/customerservice/customers/234".equals(path)) {
                    Response r = Response.status(404).entity("Can't found the customer with uri " + path).build();
                    exchange.getOut().setBody(r);
                    exchange.getOut().setFault(true);
                } else {
                    throw new RuntimeCamelException("Can't found the customer with uri " + path);
                }
            }
        }
            
    }
    private static class TestProcessor extends AbstractTestProcessor {
        public void process(Exchange exchange) throws Exception {
            Message inMessage = exchange.getIn();                        
            // Get the operation name from in message
            String operationName = inMessage.getHeader(CxfConstants.OPERATION_NAME, String.class);
            if ("getCustomer".equals(operationName)) {
                processGetCustomer(exchange);
            } else if ("updateCustomer".equals(operationName)) {
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
            
    }
    private static class TestProcessor2 extends AbstractTestProcessor {
        public void process(Exchange exchange) throws Exception {
            Message inMessage = exchange.getIn();                        
            // Get the operation name from in message
            String path = inMessage.getHeader(Exchange.HTTP_PATH, String.class);
            if (path.startsWith("/customerservice/customers")) {
                String httpMethod = inMessage.getHeader(Exchange.HTTP_METHOD, String.class);
                if (HttpMethod.GET.equals(httpMethod)) {
                    processGetCustomer(exchange);
                } else if (HttpMethod.POST.equals(httpMethod)) {
                    InputStream inBody = exchange.getIn().getBody(InputStream.class);
                    exchange.getOut().setBody(Response.ok(inBody).build());
                } 
            }
        }
            
    }
    private static class TestProcessor3 extends AbstractTestProcessor {
        public void process(Exchange exchange) throws Exception {
            UriInfo ui = exchange.getProperty(UriInfo.class.getName(), UriInfo.class);
            String path = ui.getPath();
            
            Request req = exchange.getProperty(Request.class.getName(), Request.class);
            String httpMethod = req.getMethod();
            
            if (path.startsWith("customerservice/customers") && HttpMethod.GET.equals(httpMethod)) {
                processGetCustomer(exchange);
            }
        }
            
    }
}
