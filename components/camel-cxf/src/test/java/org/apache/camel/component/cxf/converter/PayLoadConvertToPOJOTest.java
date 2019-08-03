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
package org.apache.camel.component.cxf.converter;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.non_wrapper.Person;
import org.apache.camel.non_wrapper.types.GetPerson;
import org.apache.camel.non_wrapper.types.GetPersonResponse;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class PayLoadConvertToPOJOTest extends CamelTestSupport {
    protected AbstractXmlApplicationContext applicationContext;
    
    @BeforeClass
    public static void setUpSystemProperty() {
        // just force the CXFTestSupport to load before running the test
        getPort1();
    }
    public static int getPort1() {
        return CXFTestSupport.getPort1();
    }
   
    @Override
    @Before
    public void setUp() throws Exception {
        applicationContext = createApplicationContext();
        super.setUp();
        assertNotNull("Should have created a valid spring context", applicationContext);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        IOHelper.close(applicationContext);
        super.tearDown();
    }
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        return SpringCamelContext.springCamelContext(applicationContext, true);
    }

    
    @Test
    public void testClient() throws Exception {
        
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setAddress("http://localhost:" + getPort1() + "/"
                        + getClass().getSimpleName() + "/CamelContext/RouterPort");
        factory.setServiceClass(Person.class);
        Person person = factory.create(Person.class);
        GetPerson payload = new GetPerson();
        payload.setPersonId("1234");
        
        GetPersonResponse reply = person.getPerson(payload);
        assertEquals("Get the wrong person id.", "1234",  reply.getPersonId());
        
    }
    

    
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("cxf:bean:routerEndpoint?dataFormat=PAYLOAD").streamCaching().process(new Processor() {

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // just try to turn the payload to the parameter we want
                        // to use
                        GetPerson request = exchange.getIn().getBody(GetPerson.class);

                        GetPersonResponse reply = new GetPersonResponse();
                        reply.setPersonId(request.getPersonId());
                        exchange.getOut().setBody(reply);
                    }

                });
            }
        };
    }
    
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/converter/PayloadConverterBeans.xml");
    }


}
