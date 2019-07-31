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
package org.apache.camel.component.cxf.jaxrs;

import java.util.Map;

import org.apache.camel.component.cxf.jaxrs.testbean.CustomerService;
import org.apache.camel.component.cxf.spring.SpringJAXRSClientFactoryBean;
import org.apache.camel.component.cxf.spring.SpringJAXRSServerFactoryBean;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfRsSpringEndpointTest extends CamelSpringTestSupport {
    
    private static final String BEAN_SERVICE_ENDPOINT_NAME = "serviceEndpoint";
    private static final String BEAN_SERVICE_ADDRESS = "http://localhost/programmatically";
    private static final String BEAN_SERVICE_USERNAME = "BEAN_SERVICE_USERNAME";
    private static final String BEAN_SERVICE_PASSWORD = "BEAN_SERVICE_PASSWORD";
    
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
    
    @Test
    public void testCreateCxfRsClientFactoryBeanProgrammatically() {
        
        CxfRsEndpoint endpoint = resolveMandatoryEndpoint("cxfrs://bean://" + BEAN_SERVICE_ENDPOINT_NAME, CxfRsEndpoint.class);
        SpringJAXRSClientFactoryBean cfb = (SpringJAXRSClientFactoryBean)endpoint.createJAXRSClientFactoryBean();
        
        assertNotSame("Got the same object but must be different", super.applicationContext.getBean(BEAN_SERVICE_ENDPOINT_NAME), cfb);
        assertEquals("Got the wrong address", BEAN_SERVICE_ADDRESS, cfb.getAddress());
        assertNotNull("Service class must not be null", cfb.getServiceClass());
        assertEquals("Got the wrong ServiceClass", CustomerService.class, cfb.getServiceClass());
        assertEquals("Got the wrong username", BEAN_SERVICE_USERNAME, cfb.getUsername());
        assertEquals("Got the wrong password", BEAN_SERVICE_PASSWORD, cfb.getPassword());                
    }

    public static SpringJAXRSClientFactoryBean serviceEndpoint() {

        SpringJAXRSClientFactoryBean clientFactoryBean = new SpringJAXRSClientFactoryBean();
        clientFactoryBean.setAddress(BEAN_SERVICE_ADDRESS);
        clientFactoryBean.setServiceClass(CustomerService.class);
        clientFactoryBean.setUsername(BEAN_SERVICE_USERNAME);
        clientFactoryBean.setPassword(BEAN_SERVICE_PASSWORD);

        return clientFactoryBean;
    }    
    
    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {      
        
        ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext(new String("org/apache/camel/component/cxf/jaxrs/CxfRsSpringEndpointBeans.xml"));        
        emulateBeanRegistrationProgrammatically(applicationContext);
        
        return applicationContext;
    }

    private void emulateBeanRegistrationProgrammatically(ClassPathXmlApplicationContext applicationContext) {
        
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getBeanFactory();
        BeanDefinitionBuilder definitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(CxfRsSpringEndpointTest.class.getName()).setFactoryMethod("serviceEndpoint");
        beanFactory.registerBeanDefinition(BEAN_SERVICE_ENDPOINT_NAME, definitionBuilder.getBeanDefinition());
    }
}