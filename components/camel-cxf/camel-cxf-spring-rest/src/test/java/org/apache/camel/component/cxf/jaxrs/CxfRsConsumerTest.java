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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import jakarta.servlet.ServletRequest;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.LegacyNoErrorHandlerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.jaxrs.testbean.Customer;
import org.apache.camel.component.cxf.jaxrs.testbean.CustomerService;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class CxfRsConsumerTest extends CamelTestSupport {
    private static final String PUT_REQUEST = "<Customer><name>Mary</name><id>123</id></Customer>";
    private static final String CXT = CXFTestSupport.getPort1() + "/CxfRsConsumerTest";
    // START SNIPPET: example
    private static final String CXF_RS_ENDPOINT_URI
            = "cxfrs://http://localhost:" + CXT
              + "/rest?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerServiceResource";
    private static final String CXF_RS_ENDPOINT_URI2
            = "cxfrs://http://localhost:" + CXT
              + "/rest2?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService";
    private static final String CXF_RS_ENDPOINT_URI3 = "cxfrs://http://localhost:" + CXT + "/rest3?"
                                                       + "resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerServiceNoAnnotations&"
                                                       + "modelRef=classpath:/org/apache/camel/component/cxf/jaxrs/CustomerServiceModel.xml";
    private static final String CXF_RS_ENDPOINT_URI4 = "cxfrs://http://localhost:" + CXT + "/rest4?"
                                                       + "modelRef=classpath:/org/apache/camel/component/cxf/jaxrs/CustomerServiceDefaultHandlerModel.xml";
    private static final String CXF_RS_ENDPOINT_URI5 = "cxfrs://http://localhost:" + CXT + "/rest5?"
                                                       + "propagateContexts=true&"
                                                       + "modelRef=classpath:/org/apache/camel/component/cxf/jaxrs/CustomerServiceDefaultHandlerModel.xml";
    private static final String CXF_RS_ENDPOINT_URI6 = "cxfrs://http://localhost:" + CXT + "/rest6?"
                                                       + "performInvocation=true&serviceBeans=#myServiceBean";

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        registry.bind("myServiceBean", new CustomerService());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        final Processor testProcessor = new TestProcessor();
        final Processor testProcessor2 = new TestProcessor2();
        final Processor testProcessor3 = new TestProcessor3();
        return new RouteBuilder() {
            public void configure() {
                errorHandler(new LegacyNoErrorHandlerBuilder());
                from(CXF_RS_ENDPOINT_URI).process(testProcessor);
                from(CXF_RS_ENDPOINT_URI2).process(testProcessor);
                from(CXF_RS_ENDPOINT_URI3).process(testProcessor);
                from(CXF_RS_ENDPOINT_URI4).process(testProcessor2);
                from(CXF_RS_ENDPOINT_URI5).process(testProcessor3);
                from(CXF_RS_ENDPOINT_URI6).log(LoggingLevel.OFF, "dummy");
            }
        };
    }
    // END SNIPPET: example

    private void invokeGetCustomer(String uri, String expect) throws Exception {
        HttpGet get = new HttpGet(uri);
        get.addHeader("Accept", "application/json");

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build();
             CloseableHttpResponse response = httpclient.execute(get)) {
            assertEquals(200, response.getCode());
            assertEquals(expect, EntityUtils.toString(response.getEntity()));
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
        WebTarget target
                = ClientBuilder.newClient().target("http://localhost:" + CXT + "/" + "rest4" + "/customerservice/customers");
        Customer c = target.request(MediaType.APPLICATION_JSON).post(Entity.json(new Customer(333, "Barry")), Customer.class);
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
    public void testGetCustomerImplCustomLifecycle() throws Exception {
        invokeGetCustomer("http://localhost:" + CXT + "/rest6/customerservice/customers/123",
                "{\"Customer\":{\"id\":123,\"name\":\"John\"}}");
    }

    @Test
    public void testGetWrongCustomer() throws Exception {
        URL url;

        url = new URL("http://localhost:" + CXT + "/rest/customerservice/customers/789");
        try {
            url.openStream();
            fail("Expect to get exception here");
        } catch (IOException exception) {
            // expect the Internal error exception
        }

        url = new URL("http://localhost:" + CXT + "/rest/customerservice/customers/456");
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
        StringEntity entity = new StringEntity(PUT_REQUEST, ContentType.parse("text/xml; charset=ISO-8859-1"));
        put.addHeader("test", "header1;header2");
        put.setEntity(entity);

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build();
             CloseableHttpResponse response = httpclient.execute(put)) {
            assertEquals(200, response.getCode());
            assertEquals("", EntityUtils.toString(response.getEntity()));
        }
    }

    private abstract static class AbstractTestProcessor implements Processor {
        public void processGetCustomer(Exchange exchange) throws Exception {
            Message inMessage = exchange.getIn();
            String httpMethod = inMessage.getHeader(Exchange.HTTP_METHOD, String.class);
            assertEquals("GET", httpMethod, "Get a wrong http method");
            String path = inMessage.getHeader(Exchange.HTTP_PATH, String.class);
            // The parameter of the invocation is stored in the body of in message
            String id = inMessage.getBody(String.class);
            if ("/customerservice/customers/126".equals(path)) {
                Customer customer = new Customer();
                customer.setId(Long.parseLong(id));
                customer.setName("Willem");
                // We just put the response Object into the out message body
                exchange.getMessage().setBody(customer);
            } else {
                if ("/customerservice/customers/400".equals(path)) {
                    // We return the remote client IP address this time
                    org.apache.cxf.message.Message cxfMessage
                            = inMessage.getHeader(CxfConstants.CAMEL_CXF_MESSAGE, org.apache.cxf.message.Message.class);
                    ServletRequest request = (ServletRequest) cxfMessage.get("HTTP.REQUEST");
                    // Just make sure the request object is not null
                    assertNotNull(request, "The request object should not be null");
                    Response r = Response.status(200).entity("The remoteAddress is 127.0.0.1").build();
                    exchange.getMessage().setBody(r);
                    return;
                }
                if ("/customerservice/customers/123".equals(path)) {
                    // send a customer response back
                    Response r = Response.status(200).entity("customer response back!").build();
                    exchange.getMessage().setBody(r);
                    return;
                }
                if ("/customerservice/customers/456".equals(path)) {
                    Response r = Response.status(404).entity("Can't found the customer with uri " + path)
                            .header("Content-Type", "text/plain").build();
                    throw new WebApplicationException(r);
                } else if ("/customerservice/customers/234".equals(path)) {
                    Response r = Response.status(404).entity("Can't found the customer with uri " + path)
                            .header("Content-Type", "text/plain").build();
                    exchange.getMessage().setBody(r);
                } else if ("/customerservice/customers/789".equals(path)) {
                    exchange.getMessage().setBody("Can't found the customer with uri " + path);
                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "text/plain");
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, "404");
                } else {
                    throw new RuntimeCamelException("Can't found the customer with uri " + path);
                }
            }
        }

    }

    private static class TestProcessor extends AbstractTestProcessor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Message inMessage = exchange.getIn();
            // Get the operation name from in message
            String operationName = inMessage.getHeader(CxfConstants.OPERATION_NAME, String.class);
            if ("getCustomer".equals(operationName)) {
                processGetCustomer(exchange);
            } else if ("updateCustomer".equals(operationName)) {
                assertEquals("header1;header2", inMessage.getHeader("test"), "Get a wrong customer message header");
                String httpMethod = inMessage.getHeader(Exchange.HTTP_METHOD, String.class);
                assertEquals("PUT", httpMethod, "Get a wrong http method");
                Customer customer = inMessage.getBody(Customer.class);
                assertNotNull(customer, "The customer should not be null.");
                // Now you can do what you want on the customer object
                assertEquals("Mary", customer.getName(), "Get a wrong customer name.");
                // set the response back
                exchange.getMessage().setBody(Response.ok().build());
            }

        }

    }

    private static class TestProcessor2 extends AbstractTestProcessor {
        @Override
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
                    exchange.getMessage().setBody(Response.ok(inBody).build());
                }
            }
        }

    }

    private static class TestProcessor3 extends AbstractTestProcessor {
        @Override
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
