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
package org.apache.camel.component.cxf.jaxrs;

import java.util.Map;

import org.apache.camel.component.cxf.jaxrs.testbean.CustomerService;
import org.apache.camel.component.cxf.spring.SpringJAXRSClientFactoryBean;
import org.apache.camel.component.cxf.spring.SpringJAXRSServerFactoryBean;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.cxf.version.Version;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfRsSpringEndpointTest extends CamelSpringTestSupport {
    
    @Test
    public void testCreateCxfRsServerFactoryBean() {
        CxfRsEndpoint endpoint = resolveMandatoryEndpoint("cxfrs://bean://rsServer", CxfRsEndpoint.class);
        SpringJAXRSServerFactoryBean sfb = (SpringJAXRSServerFactoryBean)endpoint.createJAXRSServerFactoryBean();
        
        assertEquals("Get a wrong provider size", 1, sfb.getProviders().size());
        assertEquals("Get a wrong beanId", sfb.getBeanId(), "rsServer");
        assertEquals("Get a wrong address", sfb.getAddress(), "http://localhost:9000/router");
        assertEquals("Get a wrong size of resource classes", sfb.getResourceClasses().size(), 1);
        assertEquals("Get a wrong resource class", sfb.getResourceClasses().get(0), CustomerService.class);
        assertEquals("Got the wrong loggingFeatureEnabled", true, sfb.isLoggingFeatureEnabled());
        assertEquals("Got the wrong loggingSizeLimit", 200, sfb.getLoggingSizeLimit());
        assertEquals("Got a wrong size of interceptors", 1, sfb.getInInterceptors().size());
        
        Map<String, Object> endpointProps = sfb.getProperties();
        // The beanId key is put by the AbstractCxfBeanDefinitionParser, so the size is 2
        assertEquals("Single endpoint property is expected", 2, endpointProps.size());
        assertEquals("Wrong property value", "aValue", endpointProps.get("aKey"));
    }
    
    @Test
    public void testCreateCxfRsClientFactoryBean() {
        CxfRsEndpoint endpoint = resolveMandatoryEndpoint("cxfrs://bean://rsClient", CxfRsEndpoint.class);
        SpringJAXRSClientFactoryBean cfb = (SpringJAXRSClientFactoryBean)endpoint.createJAXRSClientFactoryBean();
        assertEquals("Get a wrong beanId", cfb.getBeanId(), "rsClient");
        assertEquals("Get a wrong address", cfb.getAddress(), "http://localhost:9002/helloworld");        
        assertTrue("Get a wrong resource class instance", cfb.create() instanceof CustomerService);
        assertEquals("Got the wrong loggingFeatureEnabled", false, cfb.isLoggingFeatureEnabled());
        assertEquals("Got the wrong loggingSizeLimit", 0, cfb.getLoggingSizeLimit());
        assertEquals("Got a wrong size of interceptors", 1, cfb.getInInterceptors().size());

    }
    
    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        String version = Version.getCurrentVersion();
        if (version.contains("2.5") || version.contains("2.4")) {
            return new ClassPathXmlApplicationContext(new String("org/apache/camel/component/cxf/jaxrs/CxfRsSpringEndpointBeans.xml"));
        }
        return new ClassPathXmlApplicationContext(new String("org/apache/camel/component/cxf/jaxrs/CxfRsSpringEndpointBeans-2.6.xml"));
    }

}
