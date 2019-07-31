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
import org.junit.Test;

public class CxfRsClientFactoryBeanTest extends AbstractSpringBeanTestSupport {
    static int port = CXFTestSupport.getPort1();
    
    @Override
    protected String[] getApplicationContextFiles() {        
        return new String[]{"org/apache/camel/component/cxf/spring/CxfRsClientFactoryBeans.xml"};
    }
    
    @Test
    public void testCxfRsClientFactoryBean() {
        SpringJAXRSClientFactoryBean cfb = ctx.getBean("rsClient1", SpringJAXRSClientFactoryBean.class);
        assertEquals("Get a wrong address", cfb.getAddress(), "http://localhost:" + port + "/CxfRsClientFactoryBeanTest/router");
        assertEquals("Get a wrong beanId", cfb.getBeanId(), "rsClient1");
        assertEquals("Get a wrong password", cfb.getPassword(), "passwd");
        assertEquals("Get a wrong user name", cfb.getUsername(), "username");
        CustomerService customerService = cfb.create(CustomerService.class);
        assertNotNull("The customer service should not be null", customerService);
        assertEquals("Got the wrong schemalocations size", 1, cfb.getSchemaLocations().size());
        assertEquals("Got the wrong schemalocation", "classpath:wsdl/Message.xsd", cfb.getSchemaLocations().get(0));
        assertEquals("Got the wrong loggingFeatureEnabled", true, cfb.isLoggingFeatureEnabled());
        assertEquals("Got the wrong loggingSizeLimit", 200, cfb.getLoggingSizeLimit());
        Object listener = cfb.getProperties().get(FaultListener.class.getName());
        assertTrue("NullFaultListener was not added to the properties", listener instanceof NullFaultListener);
    }

}

