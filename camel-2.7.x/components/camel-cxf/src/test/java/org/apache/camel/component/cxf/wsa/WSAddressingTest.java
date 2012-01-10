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
package org.apache.camel.component.cxf.wsa;

import java.net.URL;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.headers.Header;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.hello_world_soap_http.Greeter;
import org.apache.hello_world_soap_http.GreeterImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;

/**
 *
 * @version 
 */
@ContextConfiguration
public class WSAddressingTest extends AbstractJUnit4SpringContextTests {
    
    @Autowired
    protected CamelContext context;
    protected ProducerTemplate template;
    
    private Server serviceEndpoint;
    
    @Before
    public void setUp() throws Exception {        
        template = context.createProducerTemplate();
        JaxWsServerFactoryBean svrBean = new JaxWsServerFactoryBean();
        svrBean.setAddress("http://localhost:9001/SoapContext/SoapPort");
        svrBean.setServiceClass(Greeter.class);
        svrBean.setServiceBean(new GreeterImpl());
        SpringBusFactory bf = new SpringBusFactory();
        URL cxfConfig = null;

        if (getCxfServerConfig() != null) {
            cxfConfig = ClassLoaderUtils.getResource(getCxfServerConfig(), this.getClass());
        }
        svrBean.setBus(bf.createBus(cxfConfig));
        serviceEndpoint = svrBean.create();

    }
    
    @After
    public void tearDown() throws Exception {
        if (serviceEndpoint != null) {
            serviceEndpoint.stop();
        }
    }

    @Test
    public void testWSAddressing() throws Exception {
        JaxWsProxyFactoryBean proxyFactory = new  JaxWsProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress("http://localhost:9000/SoapContext/SoapPort");
        clientBean.setServiceClass(Greeter.class);
        SpringBusFactory bf = new SpringBusFactory();
        URL cxfConfig = null;

        if (getCxfClientConfig() != null) {
            cxfConfig = ClassLoaderUtils.getResource(getCxfClientConfig(), this.getClass());
        }
        clientBean.setBus(bf.createBus(cxfConfig));
        Greeter client = (Greeter) proxyFactory.create();
        String result = client.greetMe("world!");
        assertEquals("Get a wrong response", "Hello world!", result);
    }
    
    /**
     * @return
     */
    protected String getCxfServerConfig() {
        return "server.xml";
    }
    
    /**
     * @return
     */
    protected String getCxfClientConfig() {
        return "client.xml";
    }
    
    public static class RemoveRequestOutHeaderProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            exchange.getIn().removeHeader(Header.HEADER_LIST);
        }
        
    }
    
    

}
