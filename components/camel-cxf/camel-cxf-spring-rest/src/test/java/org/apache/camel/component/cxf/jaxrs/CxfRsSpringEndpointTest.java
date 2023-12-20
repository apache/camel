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
import org.apache.camel.component.cxf.spring.jaxrs.SpringJAXRSClientFactoryBean;
import org.apache.camel.component.cxf.spring.jaxrs.SpringJAXRSServerFactoryBean;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CxfRsSpringEndpointTest extends CamelSpringTestSupport {

    private static final String BEAN_SERVICE_ENDPOINT_NAME = "serviceEndpoint";
    private static final String BEAN_SERVICE_ADDRESS = "http://localhost/programmatically";
    private static final String BEAN_SERVICE_USERNAME = "BEAN_SERVICE_USERNAME";
    private static final String BEAN_SERVICE_PASSWORD = "BEAN_SERVICE_PASSWORD";

    @Test
    public void testCreateCxfRsServerFactoryBean() {
        CxfRsEndpoint endpoint = resolveMandatoryEndpoint("cxfrs://bean://rsServer", CxfRsEndpoint.class);
        SpringJAXRSServerFactoryBean sfb = (SpringJAXRSServerFactoryBean) endpoint.createJAXRSServerFactoryBean();

        assertEquals(1, sfb.getProviders().size(), "Get a wrong provider size");
        assertEquals("rsServer", sfb.getBeanId(), "Get a wrong beanId");
        assertEquals("http://localhost:9000/router", sfb.getAddress(), "Get a wrong address");
        assertEquals(1, sfb.getResourceClasses().size(), "Get a wrong size of resource classes");
        assertEquals(CustomerService.class, sfb.getResourceClasses().get(0), "Get a wrong resource class");
        assertEquals(true, sfb.isLoggingFeatureEnabled(), "Got the wrong loggingFeatureEnabled");
        assertEquals(200, sfb.getLoggingSizeLimit(), "Got the wrong loggingSizeLimit");
        assertEquals(1, sfb.getInInterceptors().size(), "Got a wrong size of interceptors");

        Map<String, Object> endpointProps = sfb.getProperties();
        // The beanId key is put by the AbstractCxfBeanDefinitionParser, so the size is 2
        assertEquals(2, endpointProps.size(), "Single endpoint property is expected");
        assertEquals("aValue", endpointProps.get("aKey"), "Wrong property value");
    }

    @Test
    public void testCreateCxfRsClientFactoryBean() {
        CxfRsEndpoint endpoint = resolveMandatoryEndpoint("cxfrs://bean://rsClient", CxfRsEndpoint.class);
        SpringJAXRSClientFactoryBean cfb = (SpringJAXRSClientFactoryBean) endpoint.createJAXRSClientFactoryBean();
        assertEquals("rsClient", cfb.getBeanId(), "Get a wrong beanId");
        assertEquals("http://localhost:9002/helloworld", cfb.getAddress(), "Get a wrong address");
        assertTrue(cfb.create() instanceof CustomerService, "Get a wrong resource class instance");
        assertEquals(false, cfb.isLoggingFeatureEnabled(), "Got the wrong loggingFeatureEnabled");
        assertEquals(0, cfb.getLoggingSizeLimit(), "Got the wrong loggingSizeLimit");
        assertEquals(1, cfb.getInInterceptors().size(), "Got a wrong size of interceptors");

    }

    @Test
    public void testCreateCxfRsClientFactoryBeanProgrammatically() {

        CxfRsEndpoint endpoint = resolveMandatoryEndpoint("cxfrs://bean://" + BEAN_SERVICE_ENDPOINT_NAME, CxfRsEndpoint.class);
        SpringJAXRSClientFactoryBean cfb = (SpringJAXRSClientFactoryBean) endpoint.createJAXRSClientFactoryBean();

        assertNotSame(super.applicationContext.getBean(BEAN_SERVICE_ENDPOINT_NAME), cfb,
                "Got the same object but must be different");
        assertEquals(BEAN_SERVICE_ADDRESS, cfb.getAddress(), "Got the wrong address");
        assertNotNull(cfb.getServiceClass(), "Service class must not be null");
        assertEquals(CustomerService.class, cfb.getServiceClass(), "Got the wrong ServiceClass");
        assertEquals(BEAN_SERVICE_USERNAME, cfb.getUsername(), "Got the wrong username");
        assertEquals(BEAN_SERVICE_PASSWORD, cfb.getPassword(), "Got the wrong password");
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

        ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext(
                new String("org/apache/camel/component/cxf/jaxrs/CxfRsSpringEndpointBeans.xml"));
        emulateBeanRegistrationProgrammatically(applicationContext);

        return applicationContext;
    }

    private void emulateBeanRegistrationProgrammatically(ClassPathXmlApplicationContext applicationContext) {

        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getBeanFactory();
        BeanDefinitionBuilder definitionBuilder = BeanDefinitionBuilder
                .rootBeanDefinition(CxfRsSpringEndpointTest.class.getName()).setFactoryMethod("serviceEndpoint");
        beanFactory.registerBeanDefinition(BEAN_SERVICE_ENDPOINT_NAME, definitionBuilder.getBeanDefinition());
    }
}
