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
package org.apache.camel.component.cxf.spring;

import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.cxf.NullFaultListener;
import org.apache.camel.component.cxf.jaxrs.testbean.CustomerService;
import org.apache.cxf.logging.FaultListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CxfRsClientFactoryBeanTest extends AbstractSpringBeanTestSupport {
    static int port = CXFTestSupport.getPort1();

    @Override
    protected String[] getApplicationContextFiles() {
        return new String[] { "org/apache/camel/component/cxf/spring/CxfRsClientFactoryBeans.xml" };
    }

    @Test
    public void testCxfRsClientFactoryBean() {
        SpringJAXRSClientFactoryBean cfb = ctx.getBean("rsClient1", SpringJAXRSClientFactoryBean.class);
        assertEquals(cfb.getAddress(), "http://localhost:" + port + "/CxfRsClientFactoryBeanTest/router",
                "Get a wrong address");
        assertEquals("rsClient1", cfb.getBeanId(), "Get a wrong beanId");
        assertEquals("passwd", cfb.getPassword(), "Get a wrong password");
        assertEquals("username", cfb.getUsername(), "Get a wrong user name");
        CustomerService customerService = cfb.create(CustomerService.class);
        assertNotNull(customerService, "The customer service should not be null");
        assertEquals(1, cfb.getSchemaLocations().size(), "Got the wrong schemalocations size");
        assertEquals("classpath:wsdl/Message.xsd", cfb.getSchemaLocations().get(0), "Got the wrong schemalocation");
        assertEquals(true, cfb.isLoggingFeatureEnabled(), "Got the wrong loggingFeatureEnabled");
        assertEquals(200, cfb.getLoggingSizeLimit(), "Got the wrong loggingSizeLimit");
        Object listener = cfb.getProperties().get(FaultListener.class.getName());
        assertTrue(listener instanceof NullFaultListener, "NullFaultListener was not added to the properties");
    }

}
