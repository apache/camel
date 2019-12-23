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

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.cxf.CxfOperationException;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.jaxrs.testbean.Customer;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.camel.util.CastUtils;
import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.interceptor.InterceptorProvider;
import org.apache.cxf.jaxrs.utils.ParameterizedCollectionType;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;

public class CxfRsAsyncProducerTest extends CamelSpringTestSupport {
    private static int port1 = CXFTestSupport.getPort1(); 
    private static int port2 = CXFTestSupport.getPort("CxfRsAsyncProducerTest.jetty"); 
    
    public static class JettyProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            // check the query
            Message inMessage = exchange.getIn();
            exchange.getOut().setBody(inMessage.getHeader(Exchange.HTTP_QUERY, String.class));
        }
    }
    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    public int getPort1() {
        return port1;
    }
    public int getPort2() {
        return port2;
    }
    
    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {     
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/jaxrs/CxfRsSpringAsyncProducer.xml");
    }

    @Test
    public void testGetCustomerWithClientProxyAPI() {
        // START SNIPPET: ProxyExample
        Exchange exchange = template.send("direct://proxy", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                Message inMessage = exchange.getIn();
                // set the operation name 
                inMessage.setHeader(CxfConstants.OPERATION_NAME, "getCustomer");
                // using the proxy client API
                inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, Boolean.FALSE);
                // set a customer header
                inMessage.setHeader("key", "value");
                // setup the accept content type
                inMessage.setHeader(Exchange.ACCEPT_CONTENT_TYPE, "application/json");
                // set the parameters , if you just have one parameter 
                // camel will put this object into an Object[] itself
                inMessage.setBody("123");
            }
        });

        // get the response message 
        Customer response = (Customer) exchange.getOut().getBody();

        assertNotNull("The response should not be null ", response);
        assertEquals("Get a wrong customer id ", 123, response.getId());
        assertEquals("Get a wrong customer name", "John", response.getName());
        assertEquals("Get a wrong response code", 200, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Get a wrong header value", "value", exchange.getOut().getHeader("key"));
        // END SNIPPET: ProxyExample     
    }
    
    @Test
    public void testGetCustomersWithClientProxyAPI() {
        Exchange exchange = template.send("direct://proxy", newExchange -> {
            newExchange.setPattern(ExchangePattern.InOut);
            Message inMessage = newExchange.getIn();
            // set the operation name 
            inMessage.setHeader(CxfConstants.OPERATION_NAME, "getCustomers");
            // using the proxy client API
            inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, Boolean.FALSE);
            // camel will put this object into an Object[] itself
            inMessage.setBody(null);
        });
     
        // get the response message 
        List<Customer> response = CastUtils.cast((List<?>) exchange.getOut().getBody());
        
        assertNotNull("The response should not be null ", response);
        assertTrue("Dan is missing!", response.contains(new Customer(113, "Dan")));
        assertTrue("John is missing!", response.contains(new Customer(123, "John")));
        assertEquals("Get a wrong response code", 200, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    public void testGetCustomersWithHttpCentralClientAPI() {
        Exchange exchange = template.send("direct://proxy", newExchange -> {
            newExchange.setPattern(ExchangePattern.InOut);
            Message inMessage = newExchange.getIn();
            // set the Http method
            inMessage.setHeader(Exchange.HTTP_METHOD, "GET");
            // set the relative path 
            inMessage.setHeader(Exchange.HTTP_PATH, "/customerservice/customers/");
            // using the proxy client API
            inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, Boolean.TRUE);
            // set the headers 
            inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, List.class);
            inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_GENERIC_TYPE, new ParameterizedCollectionType(Customer.class));
            // camel will put this object into an Object[] itself
            inMessage.setBody(null);
        });

        // get the response message 
        List<Customer> response = CastUtils.cast((List<?>) exchange.getOut().getBody());

        assertNotNull("The response should not be null ", response);
        assertTrue("Dan is missing!", response.contains(new Customer(113, "Dan")));
        assertTrue("John is missing!", response.contains(new Customer(123, "John")));
        assertEquals("Get a wrong response code", 200, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }
    
    @Test
    public void testGetCustomerWithHttpCentralClientAPI() {
        Exchange exchange = template.send("direct://http", newExchange -> {
            newExchange.setPattern(ExchangePattern.InOut);
            Message inMessage = newExchange.getIn();
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
        });

     
        // get the response message 
        Customer response = (Customer) exchange.getOut().getBody();
        
        assertNotNull("The response should not be null ", response);
        assertEquals("Get a wrong customer id ", 123, response.getId());
        assertEquals("Get a wrong customer name", "John", response.getName());
        assertEquals("Get a wrong response code", 200, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Get a wrong header value", "value", exchange.getOut().getHeader("key"));
    }
    
    @Test
    public void testSuppressGetCustomerExceptionWithCxfRsEndpoint() {
        Exchange exchange 
            = template.send("cxfrs://http://localhost:" + getPort1() + "/" + getClass().getSimpleName() + "/?httpClientAPI=true&throwExceptionOnFailure=false", newExchange -> {
                newExchange.setPattern(ExchangePattern.InOut);
                Message message = newExchange.getIn();
                // set the Http method
                message.setHeader(Exchange.HTTP_METHOD, "PUT");
                // set the relative path
                message.setHeader(Exchange.HTTP_PATH, "/customerservice/customers");
                // we just setup the customer with a wrong id
                Customer customer = new Customer();
                customer.setId(222);
                customer.setName("user");
                message.setBody(customer);                
            });
 
        // we should get the exception here 
        assertNull("Don't expect the exception here", exchange.getException());
        Message result = exchange.getOut();
        assertEquals("Get a wrong http status code.", 406, result.getHeader(Exchange.HTTP_RESPONSE_CODE));
        
        
    }
    
    @Test
    public void testGetCustomerExceptionWithCxfRsEndpoint() {
        Exchange exchange 
            = template.send("cxfrs://http://localhost:" + getPort1() + "/" + getClass().getSimpleName() + "/?httpClientAPI=true", newExchange -> {
                newExchange.setPattern(ExchangePattern.InOut);
                Message message = newExchange.getIn();
                // set the Http method
                message.setHeader(Exchange.HTTP_METHOD, "PUT");
                // set the relative path
                message.setHeader(Exchange.HTTP_PATH, "/customerservice/customers");
                // we just setup the customer with a wrong id
                Customer customer = new Customer();
                customer.setId(222);
                customer.setName("user");
                message.setBody(customer);                
            });
 
        // we should get the exception here 
        assertNotNull("Expect the exception here", exchange.getException());
        CxfOperationException exception = (CxfOperationException)exchange.getException();
        
        assertEquals("Get a wrong response body", "Cannot find the customer!", exception.getResponseBody());
        
    }
    
    @Test
    public void testGetCustomerWithCxfRsEndpoint() {
        Exchange exchange 
            = template.send("cxfrs://http://localhost:" + getPort1() + "/" + getClass().getSimpleName() + "/?httpClientAPI=true", newExchange -> {
                newExchange.setPattern(ExchangePattern.InOut);
                Message inMessage = newExchange.getIn();
                // set the Http method
                inMessage.setHeader(Exchange.HTTP_METHOD, "GET");
                // set the relative path
                inMessage.setHeader(Exchange.HTTP_PATH, "/customerservice/customers/123");                
                // Specify the response class , cxfrs will use InputStream as the response object type 
                inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, Customer.class);
                // since we use the Get method, so we don't need to set the message body
                inMessage.setBody(null);                
            });

        // get the response message 
        Customer response = (Customer) exchange.getOut().getBody();
        assertNotNull("The response should not be null ", response);
        assertEquals("Get a wrong customer id ", 123, response.getId());
        assertEquals("Get a wrong customer name", "John", response.getName());
        assertEquals("Get a wrong response code", 200, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    public void testGetCustomerWithVariableReplacementAndCxfRsEndpoint() {
        Exchange exchange = template.send("cxfrs://http://localhost:" + getPort1() + "/" + getClass().getSimpleName() + "/?httpClientAPI=true", newExchange -> {
            newExchange.setPattern(ExchangePattern.InOut);
            Message inMessage = newExchange.getIn();
            // set the Http method
            inMessage.setHeader(Exchange.HTTP_METHOD, "GET");
            // set the relative path
            inMessage.setHeader(Exchange.HTTP_PATH, "/customerservice/customers/{customerId}");
            // Set variables for replacement
            inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_VAR_VALUES, new String[] {"123"});
            // Specify the response class , cxfrs will use InputStream as the response object type
            inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, Customer.class);
            // since we use the Get method, so we don't need to set the message body
            inMessage.setBody(null);
        });

        // get the response message
        Customer response = (Customer) exchange.getOut().getBody();
        assertNotNull("The response should not be null ", response);
        assertEquals("Get a wrong customer id ", 123, response.getId());
        assertEquals("Get a wrong customer name", "John", response.getName());
        assertEquals("Get a wrong response code", 200, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }
    
    @Test
    public void testAddCustomerUniqueResponseCodeWithHttpClientAPI() {
        Exchange exchange 
            = template.send("cxfrs://http://localhost:" + getPort1() + "/" + getClass().getSimpleName() + "?httpClientAPI=true", newExchange -> {
                newExchange.setPattern(ExchangePattern.InOut);
                Message inMessage = newExchange.getIn();
                // set the Http method
                inMessage.setHeader(Exchange.HTTP_METHOD, "POST");
                // set the relative path
                inMessage.setHeader(Exchange.HTTP_PATH, "/customerservice/customersUniqueResponseCode");                
                // create a new customer object
                Customer customer = new Customer();
                customer.setId(9999);
                customer.setName("HttpClient");
                inMessage.setBody(customer);                
            });

        // get the response message 
        Response response = (Response) exchange.getOut().getBody();
        assertNotNull("The response should not be null ", response);
        assertNotNull("The response entity should not be null", response.getEntity());
        // check the response code
        assertEquals("Get a wrong response code", 201, response.getStatus());
        // check the response code from message header
        assertEquals("Get a wrong response code", 201, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    public void testAddCustomerUniqueResponseCodeWithProxyAPI() {
        Exchange exchange = template.send("direct://proxy", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                Message inMessage = exchange.getIn();
                // set the operation name 
                inMessage.setHeader(CxfConstants.OPERATION_NAME, "addCustomerUniqueResponseCode");
                // using the proxy client API
                inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, Boolean.FALSE);
                // set the parameters , if you just have one parameter 
                // camel will put this object into an Object[] itself
                Customer customer = new Customer();
                customer.setId(8888);
                customer.setName("ProxyAPI");
                inMessage.setBody(customer);
            }
        });

        // get the response message 
        Response response = (Response) exchange.getOut().getBody();
        assertNotNull("The response should not be null ", response);
        assertNotNull("The response entity should not be null", response.getEntity());
        // check the response code
        assertEquals("Get a wrong response code", 201, response.getStatus());
        // check the response code from message header
        assertEquals("Get a wrong response code", 201, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    public void testAddCustomerUniqueResponseCode() {
        Exchange exchange
            = template.send("cxfrs://http://localhost:" + getPort1() + "/" + getClass().getSimpleName() + "?httpClientAPI=true", new Processor() {
                public void process(Exchange exchange) throws Exception {
                    exchange.setPattern(ExchangePattern.InOut);
                    Message inMessage = exchange.getIn();
                    // set the Http method
                    inMessage.setHeader(Exchange.HTTP_METHOD, "POST");
                    // set the relative path
                    inMessage.setHeader(Exchange.HTTP_PATH, "/customerservice/customersUniqueResponseCode");
                    // put the response's entity into out message body
                    inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, Customer.class);
                    // create a new customer object
                    Customer customer = new Customer();
                    customer.setId(8888);
                    customer.setName("Willem");
                    inMessage.setBody(customer);
                }
            });

        // get the response message 
        Customer response = (Customer) exchange.getOut().getBody();
        assertNotNull("The response should not be null ", response);
        assertTrue("Get a wrong customer id ", response.getId() != 8888);
        assertEquals("Get a wrong customer name", "Willem", response.getName());
        assertEquals("Get a wrong response code", 201, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }
    
    @Test
    public void testProducerWithQueryParameters() {
        Exchange exchange = template.send("cxfrs://http://localhost:" + getPort2() + "/" + getClass().getSimpleName() + "/testQuery?httpClientAPI=true&q1=12&q2=13", newExchange -> {
            newExchange.setPattern(ExchangePattern.InOut);
            Message inMessage = newExchange.getIn();
            // set the Http method
            inMessage.setHeader(Exchange.HTTP_METHOD, "GET");
            inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, InputStream.class);
            inMessage.setBody(null);                
        });

        // get the response message 
        String response = exchange.getOut().getBody(String.class);
        assertNotNull("The response should not be null ", response);
        assertEquals("The response value is wrong", "q1=12&q2=13", response);
    }
    
    @Test
    public void testProducerWithQueryParametersHeader() {
        Exchange exchange = template.send("cxfrs://http://localhost:" + getPort2() + "/" + getClass().getSimpleName() + "/testQuery?httpClientAPI=true&q1=12&q2=13", newExchange -> {
            newExchange.setPattern(ExchangePattern.InOut);
            Message inMessage = newExchange.getIn();
            // set the Http method
            inMessage.setHeader(Exchange.HTTP_METHOD, "GET");
            inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, InputStream.class);
            // override the parameter setting from URI
            // START SNIPPET: QueryMapExample
            Map<String, String> queryMap = new LinkedHashMap<>();                    
            queryMap.put("q1", "new");
            queryMap.put("q2", "world");                    
            inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_QUERY_MAP, queryMap);
            // END SNIPPET: QueryMapExample 
            inMessage.setBody(null);                
        });

        // get the response message 
        String response = exchange.getOut().getBody(String.class);
        assertNotNull("The response should not be null ", response);
        assertEquals("The response value is wrong", "q1=new&q2=world", response);
    }
    
    

    @Test    
    public void testRestServerDirectlyGetCustomer() {
        // we cannot convert directly to Customer as we need camel-jaxb
        String response = template.requestBodyAndHeader("cxfrs:http://localhost:" + getPort1() + "/" + getClass().getSimpleName() + "/customerservice/customers/123",
                null, Exchange.HTTP_METHOD, "GET", String.class);
        
        assertNotNull("The response should not be null ", response);
    }

    @Test
    public void testRestServerDirectlyAddCustomer() {
        Customer input = new Customer();
        input.setName("Donald Duck");

        // we cannot convert directly to Customer as we need camel-jaxb
        String response = template.requestBodyAndHeader("cxfrs:http://localhost:" + getPort1() + "/" + getClass().getSimpleName() + "/customerservice/customers",
                input, Exchange.HTTP_METHOD, "POST", String.class);

        assertNotNull(response);
        assertTrue(response.endsWith("<name>Donald Duck</name></Customer>"));
    }
    
    static class TestFeature implements Feature {
        boolean initialized;
        @Override
        public void initialize(InterceptorProvider interceptorProvider, Bus bus) {
            initialized = true;
        }
        @Override
        public void initialize(Client client, Bus bus) {
            //Do nothing
        }
        @Override
        public void initialize(Server server, Bus bus) {
            //Do nothing
        }
        @Override
        public void initialize(Bus bus) {
            //Do nothing
        }
    }

    @Test
    public void testProducerWithFeature() {
        TestFeature feature = context().getRegistry().lookupByNameAndType("testFeature", TestFeature.class);
        
        template.requestBodyAndHeader("cxfrs:http://localhost:" + getPort1() + "/" + getClass().getSimpleName() + "/customerservice/customers/123?features=#myFeatures",
                null, Exchange.HTTP_METHOD, "GET", String.class);

        assertTrue("The feature should be initialized", feature.initialized);
    }

    @Test
    public void testProducer422Response() {
        Exchange exchange = template.send("cxfrs://http://localhost:" + getPort1() + "/" + getClass().getSimpleName() + "/?httpClientAPI=true", newExchange -> {
            newExchange.setPattern(ExchangePattern.InOut);
            Message message = newExchange.getIn();
            // Try to create a new Customer with an invalid name
            message.setHeader(Exchange.HTTP_METHOD, "POST");
            message.setHeader(Exchange.HTTP_PATH, "/customerservice/customers");
            Customer customer = new Customer();
            customer.setId(8888);
            customer.setName("");  // will trigger a 422 response (a common REST server validation response code)
            message.setBody(customer);
        });

        assertNotNull("Expect the exception here", exchange.getException());
        assertThat("Exception should be a CxfOperationException", exchange.getException(), instanceOf(CxfOperationException.class));

        CxfOperationException cxfOperationException = CxfOperationException.class.cast(exchange.getException());

        assertThat("CXF operation exception has correct response code", cxfOperationException.getStatusCode(), is(422));
    }
}
