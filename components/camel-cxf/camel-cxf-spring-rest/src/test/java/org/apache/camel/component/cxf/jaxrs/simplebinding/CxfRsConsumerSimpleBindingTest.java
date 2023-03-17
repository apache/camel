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
package org.apache.camel.component.cxf.jaxrs.simplebinding;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import jakarta.activation.DataHandler;
import jakarta.xml.bind.JAXBContext;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.cxf.jaxrs.simplebinding.testbean.Customer;
import org.apache.camel.component.cxf.jaxrs.simplebinding.testbean.CustomerList;
import org.apache.camel.component.cxf.jaxrs.simplebinding.testbean.Order;
import org.apache.camel.component.cxf.jaxrs.simplebinding.testbean.Product;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.cxf.message.MessageContentsList;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for the Simple Binding style of CXF JAX-RS consumers.
 */
public class CxfRsConsumerSimpleBindingTest extends CamelTestSupport {
    private static final String PORT_PATH = CXFTestSupport.getPort1() + "/CxfRsConsumerTest";
    private static final String CXF_RS_ENDPOINT_URI = "cxfrs://http://localhost:" + PORT_PATH
                                                      + "/rest?resourceClasses=org.apache.camel.component.cxf.jaxrs.simplebinding.testbean.CustomerServiceResource&bindingStyle=SimpleConsumer";

    private JAXBContext jaxb;
    private CloseableHttpClient httpclient;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        httpclient = HttpClientBuilder.create().build();
        jaxb = JAXBContext.newInstance(CustomerList.class, Customer.class, Order.class, Product.class);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        httpclient.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from(CXF_RS_ENDPOINT_URI)
                        .recipientList(simple("direct:${header.operationName}"));

                from("direct:getCustomer").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        assertNotNull(exchange.getIn().getHeader("id"));
                        long id = exchange.getIn().getHeader("id", Long.class);
                        if (id == 123) {
                            assertEquals("123", exchange.getIn().getHeader("id"));
                            assertEquals(MessageContentsList.class, exchange.getIn().getBody().getClass());
                            exchange.getMessage().setBody(new Customer(123, "Raul"));
                            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                        } else if (id == 456) {
                            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                        } else {
                            fail();
                        }
                    }
                });

                from("direct:updateCustomer").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        assertEquals("123", exchange.getIn().getHeader("id"));
                        Customer c = exchange.getIn().getBody(Customer.class);
                        assertEquals(123, c.getId());
                        assertNotNull(c);
                    }
                });

                from("direct:newCustomer").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Customer c = exchange.getIn().getBody(Customer.class);
                        assertNotNull(c);
                        assertEquals(123, c.getId());
                    }
                });

                from("direct:listVipCustomers").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        assertEquals("gold", exchange.getIn().getHeader("status", String.class));
                        assertEquals(MessageContentsList.class, exchange.getIn().getBody().getClass());
                        assertEquals(0, exchange.getIn().getBody(MessageContentsList.class).size());
                        CustomerList response = new CustomerList();
                        List<Customer> list = new ArrayList<>(2);
                        list.add(new Customer(123, "Raul"));
                        list.add(new Customer(456, "Raul2"));
                        response.setCustomers(list);
                        exchange.getMessage().setBody(response);
                    }
                });

                from("direct:updateVipCustomer").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        assertEquals("gold", exchange.getIn().getHeader("status", String.class));
                        assertEquals("123", exchange.getIn().getHeader("id"));
                        Customer c = exchange.getIn().getBody(Customer.class);
                        assertEquals(123, c.getId());
                        assertNotNull(c);
                    }
                });

                from("direct:deleteVipCustomer").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        assertEquals("gold", exchange.getIn().getHeader("status", String.class));
                        assertEquals("123", exchange.getIn().getHeader("id"));
                    }
                });

                from("direct:uploadImageInputStream").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        assertEquals("123", exchange.getIn().getHeader("id"));
                        assertEquals("image/jpeg", exchange.getIn().getHeader("Content-Type"));
                        assertTrue(InputStream.class.isAssignableFrom(exchange.getIn().getBody().getClass()));
                        InputStream is = exchange.getIn().getBody(InputStream.class);
                        is.close();
                        exchange.getMessage().setBody(null);
                    }
                });

                from("direct:uploadImageDataHandler").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        assertEquals("123", exchange.getIn().getHeader("id"));
                        assertEquals("image/jpeg", exchange.getIn().getHeader("Content-Type"));
                        assertTrue(DataHandler.class.isAssignableFrom(exchange.getIn().getBody().getClass()));
                        DataHandler dh = exchange.getIn().getBody(DataHandler.class);
                        assertEquals("image/jpeg", dh.getContentType());
                        dh.getInputStream().close();
                        exchange.getMessage().setBody(null);
                    }
                });

                from("direct:multipartPostWithParametersAndPayload").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        assertEquals("abcd", exchange.getIn().getHeader("query"));
                        assertEquals("123", exchange.getIn().getHeader("id"));
                        assertNotNull(exchange.getIn(AttachmentMessage.class).getAttachment("part1"));
                        assertNotNull(exchange.getIn(AttachmentMessage.class).getAttachment("part2"));
                        assertNull(exchange.getIn().getHeader("part1"));
                        assertNull(exchange.getIn().getHeader("part2"));
                        assertEquals(Customer.class, exchange.getIn().getHeader("body").getClass());
                        exchange.getMessage().setBody(null);
                    }
                });

                from("direct:multipartPostWithoutParameters").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        assertNotNull(exchange.getIn(AttachmentMessage.class).getAttachment("part1"));
                        assertNotNull(exchange.getIn(AttachmentMessage.class).getAttachment("part2"));
                        assertNull(exchange.getIn().getHeader("part1"));
                        assertNull(exchange.getIn().getHeader("part2"));
                        assertEquals(Customer.class, exchange.getIn().getHeader("body").getClass());
                        exchange.getMessage().setBody(null);
                    }
                });
            }
        };
    }

    @Test
    public void testGetCustomerOnlyHeaders() throws Exception {
        HttpGet get = new HttpGet("http://localhost:" + PORT_PATH + "/rest/customerservice/customers/123");
        get.addHeader("Accept", "text/xml");
        CloseableHttpResponse response = httpclient.execute(get);
        assertEquals(200, response.getCode());
        Customer entity = (Customer) jaxb.createUnmarshaller().unmarshal(response.getEntity().getContent());
        assertEquals(123, entity.getId());
    }

    @Test
    public void testGetCustomerHttp404CustomStatus() throws Exception {
        HttpGet get = new HttpGet("http://localhost:" + PORT_PATH + "/rest/customerservice/customers/456");
        get.addHeader("Accept", "text/xml");
        CloseableHttpResponse response = httpclient.execute(get);
        assertEquals(404, response.getCode());
    }

    @Test
    public void testUpdateCustomerBodyAndHeaders() throws Exception {
        HttpPut put = new HttpPut("http://localhost:" + PORT_PATH + "/rest/customerservice/customers/123");
        StringWriter sw = new StringWriter();
        jaxb.createMarshaller().marshal(new Customer(123, "Raul"), sw);
        put.setEntity(new StringEntity(sw.toString()));
        put.addHeader("Content-Type", "text/xml");
        put.addHeader("Accept", "text/xml");
        CloseableHttpResponse response = httpclient.execute(put);
        assertEquals(200, response.getCode());
    }

    @Test
    public void testNewCustomerOnlyBody() throws Exception {
        HttpPost post = new HttpPost("http://localhost:" + PORT_PATH + "/rest/customerservice/customers");
        StringWriter sw = new StringWriter();
        jaxb.createMarshaller().marshal(new Customer(123, "Raul"), sw);
        post.setEntity(new StringEntity(sw.toString()));
        post.addHeader("Content-Type", "text/xml");
        post.addHeader("Accept", "text/xml");
        CloseableHttpResponse response = httpclient.execute(post);
        assertEquals(200, response.getCode());
    }

    @Test
    public void testListVipCustomers() throws Exception {
        HttpGet get = new HttpGet("http://localhost:" + PORT_PATH + "/rest/customerservice/customers/vip/gold");
        get.addHeader("Content-Type", "text/xml");
        get.addHeader("Accept", "text/xml");
        CloseableHttpResponse response = httpclient.execute(get);
        assertEquals(200, response.getCode());
        CustomerList cl = (CustomerList) jaxb.createUnmarshaller()
                .unmarshal(new StringReader(EntityUtils.toString(response.getEntity())));
        List<Customer> vips = cl.getCustomers();
        assertEquals(2, vips.size());
        assertEquals(123, vips.get(0).getId());
        assertEquals(456, vips.get(1).getId());
    }

    @Test
    public void testUpdateVipCustomer() throws Exception {
        HttpPut put = new HttpPut("http://localhost:" + PORT_PATH + "/rest/customerservice/customers/vip/gold/123");
        StringWriter sw = new StringWriter();
        jaxb.createMarshaller().marshal(new Customer(123, "Raul2"), sw);
        put.setEntity(new StringEntity(sw.toString()));
        put.addHeader("Content-Type", "text/xml");
        put.addHeader("Accept", "text/xml");
        CloseableHttpResponse response = httpclient.execute(put);
        assertEquals(200, response.getCode());
    }

    @Test
    public void testDeleteVipCustomer() throws Exception {
        HttpDelete delete = new HttpDelete("http://localhost:" + PORT_PATH + "/rest/customerservice/customers/vip/gold/123");
        delete.addHeader("Accept", "text/xml");
        CloseableHttpResponse response = httpclient.execute(delete);
        assertEquals(200, response.getCode());
    }

    @Test
    public void testUploadInputStream() throws Exception {
        HttpPost post = new HttpPost("http://localhost:" + PORT_PATH + "/rest/customerservice/customers/123/image_inputstream");
        post.addHeader("Content-Type", "image/jpeg");
        post.addHeader("Accept", "text/xml");
        post.setEntity(new InputStreamEntity(this.getClass().getClassLoader().getResourceAsStream("java.jpg"), 100, null));
        CloseableHttpResponse response = httpclient.execute(post);
        assertEquals(200, response.getCode());
    }

    @Test
    public void testUploadDataHandler() throws Exception {
        HttpPost post = new HttpPost("http://localhost:" + PORT_PATH + "/rest/customerservice/customers/123/image_datahandler");
        post.addHeader("Content-Type", "image/jpeg");
        post.addHeader("Accept", "text/xml");
        post.setEntity(new InputStreamEntity(this.getClass().getClassLoader().getResourceAsStream("java.jpg"), 100, null));
        CloseableHttpResponse response = httpclient.execute(post);
        assertEquals(200, response.getCode());
    }

    @Test
    public void testMultipartPostWithParametersAndPayload() throws Exception {
        HttpPost post
                = new HttpPost("http://localhost:" + PORT_PATH + "/rest/customerservice/customers/multipart/123?query=abcd");
        MultipartEntityBuilder builder = MultipartEntityBuilder.create().setMode(HttpMultipartMode.STRICT);
        builder.addBinaryBody("part1", new File(this.getClass().getClassLoader().getResource("java.jpg").toURI()),
                ContentType.create("image/jpeg"), "java.jpg");
        builder.addBinaryBody("part2", new File(this.getClass().getClassLoader().getResource("java.jpg").toURI()),
                ContentType.create("image/jpeg"), "java.jpg");
        StringWriter sw = new StringWriter();
        jaxb.createMarshaller().marshal(new Customer(123, "Raul"), sw);
        builder.addTextBody("body", sw.toString(), ContentType.TEXT_XML);
        post.setEntity(builder.build());
        CloseableHttpResponse response = httpclient.execute(post);
        assertEquals(200, response.getCode());
    }

    @Test
    public void testMultipartPostWithoutParameters() throws Exception {
        HttpPost post
                = new HttpPost("http://localhost:" + PORT_PATH + "/rest/customerservice/customers/multipart/withoutParameters");
        MultipartEntityBuilder builder = MultipartEntityBuilder.create().setMode(HttpMultipartMode.STRICT);
        builder.addBinaryBody("part1", new File(this.getClass().getClassLoader().getResource("java.jpg").toURI()),
                ContentType.create("image/jpeg"), "java.jpg");
        builder.addBinaryBody("part2", new File(this.getClass().getClassLoader().getResource("java.jpg").toURI()),
                ContentType.create("image/jpeg"), "java.jpg");
        StringWriter sw = new StringWriter();
        jaxb.createMarshaller().marshal(new Customer(123, "Raul"), sw);
        builder.addTextBody("body", sw.toString(), ContentType.TEXT_XML);
        post.setEntity(builder.build());
        CloseableHttpResponse response = httpclient.execute(post);
        assertEquals(200, response.getCode());
    }

}
