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
package org.apache.camel.component.cxf;

import java.net.URL;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;

import org.w3c.dom.Element;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.pizza.Pizza;
import org.apache.camel.pizza.PizzaService;
import org.apache.camel.pizza.types.CallerIDHeaderType;
import org.apache.camel.pizza.types.OrderPizzaResponseType;
import org.apache.camel.pizza.types.OrderPizzaType;
import org.apache.camel.pizza.types.ToppingsListType;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.headers.Header;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;



public class CxfPayLoadSoapHeaderTest extends CxfRouterTestSupport {
    protected AbstractXmlApplicationContext applicationContext; 
    private final QName serviceName = new QName("http://camel.apache.org/pizza", "PizzaService");
    
    
    private String routerEndpointURI = "cxf:bean:routerEndpoint?dataFormat=PAYLOAD";
    private String serviceEndpointURI = "cxf:bean:serviceEndpoint?dataFormat=PAYLOAD";
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: payload
                from(routerEndpointURI).process(new Processor() {
                    @SuppressWarnings("unchecked")
                    public void process(Exchange exchange) throws Exception {
                        Message inMessage = exchange.getIn();
                        CxfMessage message = (CxfMessage) inMessage;
                        List<Element> elements = message.getMessage().get(List.class);
                        assertNotNull("We should get the payload elements here" , elements);
                        assertEquals("Get the wrong elements size" , elements.size(), 1);
                        assertEquals("Get the wrong namespace URI" , elements.get(0).getNamespaceURI(), "http://camel.apache.org/pizza/types");
                            
                        List<SoapHeader> headers = CastUtils.cast((List<?>)message.getMessage().get(Header.HEADER_LIST));
                        assertNotNull("We should get the headers here", headers);
                        assertEquals("Get the wrong headers size", headers.size(), 1);
                        assertEquals("Get the wrong namespace URI" , ((Element)(headers.get(0).getObject())).getNamespaceURI(), "http://camel.apache.org/pizza/types");
                        
                    }
                    
                })
                .to(serviceEndpointURI);
                // END SNIPPET: payload
            }
        };
    }

    @Override
    protected void setUp() throws Exception {
        applicationContext = createApplicationContext();
        super.setUp();
        assertNotNull("Should have created a valid spring context", applicationContext);


    }

    @Override
    protected void tearDown() throws Exception {
        if (applicationContext != null) {
            applicationContext.destroy();
        }
        super.tearDown();
    }

    @Override
    protected void startService() {
        Object implementor = new PizzaImpl();
        String address = "http://localhost:9023/new_pizza_service/services/PizzaService";
        EndpointImpl endpoint = (EndpointImpl) Endpoint.publish(address, implementor);
        server = endpoint.getServer();
    }
    
    public void testPizzaService() {
        Pizza port = getPort();

        OrderPizzaType req = new OrderPizzaType();
        ToppingsListType t = new ToppingsListType();
        t.getTopping().add("test");
        req.setToppings(t);

        CallerIDHeaderType header = new CallerIDHeaderType();
        header.setName("Willem");
        header.setPhoneNumber("108");

        OrderPizzaResponseType res =  port.orderPizza(req, header);

        assertEquals(208, res.getMinutesUntilReady());
    }
    
    private Pizza getPort() {
        URL wsdl = getClass().getResource("/pizza_service.wsdl");
        assertNotNull("WSDL is null", wsdl);

        PizzaService service = new PizzaService(wsdl, serviceName);
        assertNotNull("Service is null ", service);

        return service.getPizzaPort();
    }
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        return SpringCamelContext.springCamelContext(applicationContext);
    }


    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/PizzaEndpoints.xml");
    }
    
    

}
