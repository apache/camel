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
package org.apache.camel.component.cxf.wsa;

import java.net.URL;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.headers.Header;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.hello_world_soap_http.Greeter;
import org.apache.hello_world_soap_http.GreeterImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration
public class WSAddressingTest {

    private static AvailablePortFinder.Port PORT1;
    private static AvailablePortFinder.Port PORT2;
    private static AvailablePortFinder.Port PORT3;

    @BeforeAll
    static void allocateWSAddressingTestPorts() {
        PORT1 = AvailablePortFinder.find();
        System.setProperty("WSAddressingTest.port1", PORT1.toString());
        PORT2 = AvailablePortFinder.find();
        System.setProperty("WSAddressingTest.port2", PORT2.toString());
        PORT3 = AvailablePortFinder.find();
        System.setProperty("WSAddressingTest.port3", PORT3.toString());
        port0 = PORT1.getPort();
        port1 = PORT2.getPort();
        port2 = PORT3.getPort();
    }

    @AfterAll
    static void releaseWSAddressingTestPorts() {
        System.clearProperty("WSAddressingTest.port1");
        if (PORT1 != null) {
            PORT1.release();
        }
        System.clearProperty("WSAddressingTest.port2");
        if (PORT2 != null) {
            PORT2.release();
        }
        System.clearProperty("WSAddressingTest.port3");
        if (PORT3 != null) {
            PORT3.release();
        }
    }

    protected static int port0;
    protected static int port1;
    protected static int port2;

    @Autowired
    protected CamelContext context;
    protected ProducerTemplate template;

    private Server serviceEndpoint;

    protected String getServerAddress() {
        return "http://localhost:" + port1 + "/" + this.getClass().getSimpleName() + "/SoapContext/SoapPort";
    }

    protected String getClientAddress() {
        return "http://localhost:" + port0 + "/" + this.getClass().getSimpleName() + "/SoapContext/SoapPort";
    }

    @BeforeEach
    public void setUp() throws Exception {

        template = context.createProducerTemplate();
        JaxWsServerFactoryBean svrBean = new JaxWsServerFactoryBean();
        svrBean.setAddress(getServerAddress());
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

    @AfterEach
    public void tearDown() throws Exception {
        if (serviceEndpoint != null) {
            serviceEndpoint.stop();
        }
    }

    @Test
    public void testWSAddressing() throws Exception {
        JaxWsProxyFactoryBean proxyFactory = new JaxWsProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(getClientAddress());
        clientBean.setServiceClass(Greeter.class);
        SpringBusFactory bf = new SpringBusFactory();
        URL cxfConfig = null;

        if (getCxfClientConfig() != null) {
            cxfConfig = ClassLoaderUtils.getResource(getCxfClientConfig(), this.getClass());
        }
        proxyFactory.setBus(bf.createBus(cxfConfig));
        Greeter client = (Greeter) proxyFactory.create();
        String result = client.greetMe("world!");
        assertEquals("Hello world!", result, "Get a wrong response");
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

        @Override
        public void process(Exchange exchange) throws Exception {
            List<?> headerList = (List<?>) exchange.getIn().getHeader(Header.HEADER_LIST);
            assertNotNull(headerList, "We should get the header list.");
            assertEquals(4, headerList.size(), "Get a wrong size of header list.");
            // we don't need send the soap headers to the client
            exchange.getIn().removeHeader(Header.HEADER_LIST);
        }

    }

}
