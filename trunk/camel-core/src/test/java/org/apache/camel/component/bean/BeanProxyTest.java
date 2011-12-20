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
package org.apache.camel.component.bean;

import org.w3c.dom.Document;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.ProxyBuilder;
import org.apache.camel.builder.RouteBuilder;


/**
 * @version 
 */
public class BeanProxyTest extends ContextTestSupport {

    public void testBeanProxyStringReturnString() throws Exception {
        // START SNIPPET: e2
        Endpoint endpoint = context.getEndpoint("direct:start");
        OrderService service = ProxyHelper.createProxy(endpoint, OrderService.class);

        String reply = service.submitOrderStringReturnString("<order type=\"book\">Camel in action</order>");
        assertEquals("<order id=\"123\">OK</order>", reply);
        // END SNIPPET: e2
    }

    public void testBeanProxyStringReturnDocument() throws Exception {
        Endpoint endpoint = context.getEndpoint("direct:start");
        OrderService service = ProxyHelper.createProxy(endpoint, OrderService.class);

        Document reply = service.submitOrderStringReturnDocument("<order type=\"book\">Camel in action</order>");
        assertNotNull(reply);
        String s = context.getTypeConverter().convertTo(String.class, reply);
        assertEquals("<order id=\"123\">OK</order>", s);
    }

    public void testBeanProxyDocumentReturnString() throws Exception {
        Endpoint endpoint = context.getEndpoint("direct:start");
        OrderService service = ProxyHelper.createProxy(endpoint, OrderService.class);

        Document doc = context.getTypeConverter().convertTo(Document.class, "<order type=\"book\">Camel in action</order>");

        String reply = service.submitOrderDocumentReturnString(doc);
        assertEquals("<order id=\"123\">OK</order>", reply);
    }

    public void testBeanProxyDocumentReturnDocument() throws Exception {
        // START SNIPPET: e3
        Endpoint endpoint = context.getEndpoint("direct:start");
        OrderService service = ProxyHelper.createProxy(endpoint, OrderService.class);

        Document doc = context.getTypeConverter().convertTo(Document.class, "<order type=\"book\">Camel in action</order>");

        Document reply = service.submitOrderDocumentReturnDocument(doc);
        assertNotNull(reply);
        String s = context.getTypeConverter().convertTo(String.class, reply);
        assertEquals("<order id=\"123\">OK</order>", s);
        // END SNIPPET: e3
    }

    public void testBeanProxyFailure() throws Exception {
        Endpoint endpoint = context.getEndpoint("direct:start");
        OrderService service = ProxyHelper.createProxy(endpoint, OrderService.class);

        String reply = service.submitOrderStringReturnString("<order type=\"beer\">Carlsberg</order>");
        assertEquals("<order>FAIL</order>", reply);
    }

    public void testBeanProxyFailureNotXMLBody() throws Exception {
        Endpoint endpoint = context.getEndpoint("direct:start");
        OrderService service = ProxyHelper.createProxy(endpoint, OrderService.class);

        try {
            service.submitOrderStringReturnString("Hello World");
            fail("Should have thrown exception");
        } catch (Exception e) {
            // expected
        }
    }

    public void testBeanProxyVoidReturnType() throws Exception {
        Endpoint endpoint = context.getEndpoint("direct:start");
        OrderService service = ProxyHelper.createProxy(endpoint, OrderService.class);

        service.doNothing("<order>ping</order>");
    }

    public void testBeanProxyFailureInvalidReturnType() throws Exception {
        Endpoint endpoint = context.getEndpoint("direct:start");
        OrderService service = ProxyHelper.createProxy(endpoint, OrderService.class);

        try {
            service.invalidReturnType("<order type=\"beer\">Carlsberg</order>");
            fail("Should have thrown exception");
        } catch (Exception e) {
            // expected
            InvalidPayloadException cause = assertIsInstanceOf(InvalidPayloadException.class, e.getCause());
            assertEquals(Integer.class, cause.getType());
        }
    }

    public void testBeanProxyCallAnotherBean() throws Exception {
        Endpoint endpoint = context.getEndpoint("direct:bean");
        OrderService service = ProxyHelper.createProxy(endpoint, OrderService.class);

        String reply = service.submitOrderStringReturnString("World");
        assertEquals("Hello World", reply);
    }

    // START SNIPPET: e4
    public void testProxyBuilderProxyCallAnotherBean() throws Exception {
        // use ProxyBuilder to easily create the proxy
        OrderService service = new ProxyBuilder(context).endpoint("direct:bean").build(OrderService.class);

        String reply = service.submitOrderStringReturnString("World");
        assertEquals("Hello World", reply);
    }
    // END SNIPPET: e4

    public void testBeanProxyCallAnotherBeanWithNoArgs() throws Exception {
        Endpoint endpoint = context.getEndpoint("direct:bean");
        OrderService service = ProxyHelper.createProxy(endpoint, OrderService.class);

        String reply = service.doAbsolutelyNothing();
        assertEquals("Hi nobody", reply);
    }

    public void testProxyBuilderProxyCallAnotherBeanWithNoArgs() throws Exception {
        Endpoint endpoint = context.getEndpoint("direct:bean");
        OrderService service = new ProxyBuilder(context).endpoint(endpoint).build(OrderService.class);

        String reply = service.doAbsolutelyNothing();
        assertEquals("Hi nobody", reply);
    }

    public void testBeanProxyVoidAsInOut() throws Exception {
        Endpoint endpoint = context.getEndpoint("seda:delay");
        // will by default let all exchanges be InOut
        OrderService service = ProxyHelper.createProxy(endpoint, OrderService.class);

        getMockEndpoint("mock:delay").expectedBodiesReceived("Hello World", "Bye World");
        service.doNothing("Hello World");
        template.sendBody("mock:delay", "Bye World");

        assertMockEndpointsSatisfied();
    }

    public void testProxyBuilderVoidAsInOut() throws Exception {
        // will by default let all exchanges be InOut
        OrderService service = new ProxyBuilder(context).endpoint("seda:delay").build(OrderService.class);

        getMockEndpoint("mock:delay").expectedBodiesReceived("Hello World", "Bye World");
        service.doNothing("Hello World");
        template.sendBody("mock:delay", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                        .choice()
                        .when(xpath("/order/@type = 'book'")).to("direct:book")
                        .otherwise().to("direct:other")
                        .end();

                from("direct:book").transform(constant("<order id=\"123\">OK</order>"));

                from("direct:other").transform(constant("<order>FAIL</order>"));
                // END SNIPPET: e1

                from("direct:bean")
                        .bean(MyFooBean.class, "hello");

                from("seda:delay")
                        .delay(1000)
                        .to("mock:delay");
            }
        };
    }

    public static class MyFooBean {

        public String hello(String name) {
            if (name != null) {
                return "Hello " + name;
            } else {
                return "Hi nobody";
            }
        }
    }
}
