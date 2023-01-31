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
package org.apache.camel.component.cxf;

import java.net.URL;
import java.util.List;

import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Endpoint;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Element;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.cxf.common.CxfPayload;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.pizza.Pizza;
import org.apache.camel.pizza.PizzaService;
import org.apache.camel.pizza.types.CallerIDHeaderType;
import org.apache.camel.pizza.types.OrderPizzaResponseType;
import org.apache.camel.pizza.types.OrderPizzaType;
import org.apache.camel.pizza.types.ToppingsListType;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.headers.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CxfPayLoadSoapHeaderSpringTest extends CamelSpringTestSupport {
    static int port1 = CXFTestSupport.getPort1();
    static int port2 = CXFTestSupport.getPort2();
    private final QName serviceName = new QName("http://camel.apache.org/pizza", "PizzaService");

    protected void start(String n) {
        Object implementor = new PizzaImpl();
        String address = "http://localhost:" + port2 + "/" + n
                         + "/new_pizza_service/services/PizzaService";
        Endpoint.publish(address, implementor);
    }

    @BeforeEach
    public void startService() {
        start(getClass().getSimpleName());
    }

    protected String getRouterEndpointURI() {
        return "cxf:bean:routerEndpoint?dataFormat=PAYLOAD";
    }

    protected String getServiceEndpointURI() {
        return "cxf:bean:serviceEndpoint?dataFormat=PAYLOAD";
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/PizzaEndpoints.xml");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: payload
                from(getRouterEndpointURI()).process(new Processor() {
                    @SuppressWarnings("unchecked")
                    public void process(Exchange exchange) throws Exception {
                        CxfPayload<SoapHeader> payload = exchange.getIn().getBody(CxfPayload.class);
                        List<Source> elements = payload.getBodySources();
                        assertNotNull(elements, "We should get the elements here");
                        assertEquals(1, elements.size(), "Get the wrong elements size");

                        Element el = new XmlConverter().toDOMElement(elements.get(0));
                        elements.set(0, new DOMSource(el));
                        assertEquals("http://camel.apache.org/pizza/types",
                                el.getNamespaceURI(), "Get the wrong namespace URI");

                        List<SoapHeader> headers = payload.getHeaders();
                        assertNotNull(headers, "We should get the headers here");
                        assertEquals(1, headers.size(), "Get the wrong headers size");
                        assertEquals("http://camel.apache.org/pizza/types",
                                ((Element) (headers.get(0).getObject())).getNamespaceURI(), "Get the wrong namespace URI");
                        // alternatively you can also get the SOAP header via the camel header:
                        headers = exchange.getIn().getHeader(Header.HEADER_LIST, List.class);
                        assertNotNull(headers, "We should get the headers here");
                        assertEquals(1, headers.size(), "Get the wrong headers size");
                        assertEquals("http://camel.apache.org/pizza/types",
                                ((Element) (headers.get(0).getObject())).getNamespaceURI(), "Get the wrong namespace URI");

                    }

                })
                        .to(getServiceEndpointURI());
                // END SNIPPET: payload
            }
        };
    }

    @Test
    public void testPizzaService() {
        Pizza port = getPort();

        OrderPizzaType req = new OrderPizzaType();
        ToppingsListType t = new ToppingsListType();
        t.getTopping().add("test");
        req.setToppings(t);

        CallerIDHeaderType header = new CallerIDHeaderType();
        header.setName("Willem");
        header.setPhoneNumber("108");

        OrderPizzaResponseType res = port.orderPizza(req, header);

        assertEquals(208, res.getMinutesUntilReady());
    }

    private Pizza getPort() {
        URL wsdl = getClass().getResource("/pizza_service.wsdl");
        assertNotNull(wsdl, "WSDL is null");

        PizzaService service = new PizzaService(wsdl, serviceName);
        assertNotNull(service, "Service is null");

        Pizza pizza = service.getPizzaPort();
        ((BindingProvider) pizza).getRequestContext()
                .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                        "http://localhost:" + port1 + "/" + getClass().getSimpleName()
                                                                + "/pizza_service/services/PizzaService");
        return pizza;
    }

}
