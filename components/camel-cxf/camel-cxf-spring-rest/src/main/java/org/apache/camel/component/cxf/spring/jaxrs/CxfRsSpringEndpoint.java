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
package org.apache.camel.component.cxf.spring.jaxrs;

import org.apache.camel.Component;
import org.apache.camel.component.cxf.jaxrs.BeanIdAware;
import org.apache.camel.component.cxf.jaxrs.CxfRsEndpoint;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.cxf.configuration.spring.ConfigurerImpl;
import org.apache.cxf.jaxrs.AbstractJAXRSFactoryBean;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;

public class CxfRsSpringEndpoint extends CxfRsEndpoint implements BeanIdAware {
    private AbstractJAXRSFactoryBean bean;
    private ConfigurerImpl configurer;
    private String beanId;

    public CxfRsSpringEndpoint(Component component, String uri, AbstractJAXRSFactoryBean bean) {
        super(uri, component);
        setAddress(bean.getAddress());
        setFeatures(bean.getFeatures());
        setProperties(bean.getProperties());
        // Update the sfb address by resolving the properties
        bean.setAddress(getAddress());
        init(bean);
    }

    private void init(AbstractJAXRSFactoryBean bean) {
        this.bean = bean;
        if (bean instanceof BeanIdAware beanIdAware) {
            setBeanId(beanIdAware.getBeanId());
        }

        if (getCamelContext() instanceof SpringCamelContext springCamelContext) {
            ApplicationContext applicationContext = springCamelContext.getApplicationContext();
            configurer = new ConfigurerImpl(applicationContext);
        }
    }

    @Override
    protected JAXRSServerFactoryBean newJAXRSServerFactoryBean() {
        checkBeanType(bean, JAXRSServerFactoryBean.class);
        return (JAXRSServerFactoryBean) bean;
    }

    @Override
    protected void setupJAXRSServerFactoryBean(JAXRSServerFactoryBean sfb) {
        if (sfb instanceof SpringJAXRSServerFactoryBean springBean) {
            springBean.setPerformInvocation(isPerformInvocation());
        }
        super.setupJAXRSServerFactoryBean(sfb);
    }

    @Override
    protected JAXRSClientFactoryBean newJAXRSClientFactoryBean() {
        checkBeanType(bean, JAXRSClientFactoryBean.class);
        return newInstanceWithCommonProperties();
    }

    @Override
    protected void setupJAXRSClientFactoryBean(JAXRSClientFactoryBean cfb, String address) {
        // apply Spring bean config first so URI options can override
        if (configurer != null) {
            configurer.configureBean(beanId, cfb);
        }
        if (getModelRef() != null) {
            cfb.setModelRef(getModelRef());
        }
        if (getResourceClasses() != null && !getResourceClasses().isEmpty()) {
            cfb.setResourceClass(getResourceClasses().get(0));
            cfb.getServiceFactory().setResourceClasses(getResourceClasses());
        }
        setupCommonFactoryProperties(cfb);
        cfb.setThreadSafe(true);
        getNullSafeCxfRsEndpointConfigurer().configure(cfb);
        if (address != null) {
            cfb.setAddress(address);
        }
    }

    @Override
    public String getBeanId() {
        return beanId;
    }

    @Override
    public void setBeanId(String id) {
        this.beanId = id;
    }

    private JAXRSClientFactoryBean newInstanceWithCommonProperties() {
        SpringJAXRSClientFactoryBean cfb = new SpringJAXRSClientFactoryBean();

        if (bean instanceof SpringJAXRSClientFactoryBean) {
            ReflectionUtils.shallowCopyFieldState(bean, cfb);
        }

        return cfb;
    }
}
