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
package org.apache.camel.component.cxf.wsrm;

import java.net.URL;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
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
public class WSRMTest extends AbstractJUnit4SpringContextTests {
    
    protected static int port1 = CXFTestSupport.getPort2(); 
    protected static int port2 = CXFTestSupport.getPort3();
    
    @Autowired
    protected CamelContext context;
    
    protected String getClientAddress() {
        return "http://localhost:" + port1 + "/wsrm/HelloWorld";
    }

    @Test
    public void testWSAddressing() throws Exception {
        JaxWsProxyFactoryBean proxyFactory = new  JaxWsProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(getClientAddress());
        clientBean.setServiceClass(HelloWorld.class);
        clientBean.setWsdlURL(WSRMTest.class.getResource("/HelloWorld.wsdl").toString());
        SpringBusFactory bf = new SpringBusFactory();
        URL cxfConfig = null;

        if (getCxfClientConfig() != null) {
            cxfConfig = ClassLoaderUtils.getResource(getCxfClientConfig(), this.getClass());
        }
        proxyFactory.setBus(bf.createBus(cxfConfig));
        proxyFactory.getOutInterceptors().add(new MessageLossSimulator());
        HelloWorld client = (HelloWorld) proxyFactory.create();
        String result = client.sayHi("world!");
        assertEquals("Get a wrong response", "Hello world!", result);
    }
   
    /**
     * @return
     */
    protected String getCxfClientConfig() {
        return "ws_rm.xml";
    }
    

}
