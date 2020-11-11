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

import java.util.List;

import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.cxf.jaxrs.testbean.CustomerService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CxfRsServerFactoryBeanTest extends AbstractSpringBeanTestSupport {
    static int port = CXFTestSupport.getPort1();

    @Override
    protected String[] getApplicationContextFiles() {
        return new String[] { "org/apache/camel/component/cxf/spring/CxfRsServerFactoryBeans.xml" };
    }

    @Test
    public void testCxfRsServerFactoryBean() {
        SpringJAXRSServerFactoryBean sfb1 = ctx.getBean("rsServer1", SpringJAXRSServerFactoryBean.class);
        assertEquals(sfb1.getAddress(), "http://localhost:" + port + "/CxfRsServerFactoryBeanTest/server1");
        List<Class<?>> resource1Classes = sfb1.getResourceClasses();
        assertEquals(1, resource1Classes.size(), "Get a wrong size of resouceClasses");
        assertEquals(resource1Classes.get(0), CustomerService.class, "Get a wrong resource class");

        SpringJAXRSServerFactoryBean sfb2 = ctx.getBean("rsServer2", SpringJAXRSServerFactoryBean.class);
        assertEquals(sfb2.getAddress(), "http://localhost:" + port + "/CxfRsServerFactoryBeanTest/server2",
                "Get a wrong address");
        sfb2.getResourceClasses();
        List<Class<?>> resource2Classes = sfb2.getResourceClasses();
        assertEquals(1, resource2Classes.size(), "Get a wrong size of resouceClasses");
        assertEquals(resource2Classes.get(0), CustomerService.class, "Get a wrong resource class");
        assertEquals(1, sfb2.getSchemaLocations().size(), "Got the wrong schemalocations size");
        assertEquals("classpath:wsdl/Message.xsd", sfb2.getSchemaLocations().get(0), "Got the wrong schemalocation");
    }

}
