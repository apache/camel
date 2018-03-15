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

import org.apache.camel.Component;
import org.apache.camel.component.cxf.spring.SpringJAXRSClientFactoryBean;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.cxf.configuration.spring.ConfigurerImpl;
import org.apache.cxf.jaxrs.AbstractJAXRSFactoryBean;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.springframework.context.ApplicationContext;

public class CxfRsSpringEndpoint extends CxfRsEndpoint implements BeanIdAware {
    private AbstractJAXRSFactoryBean bean;
    private ConfigurerImpl configurer;
    private String beanId;

    @Deprecated
    /**
     * It will be removed in Camel 3.0
     * 
     * @param comp
     * @param bean
     */
    public CxfRsSpringEndpoint(Component component, AbstractJAXRSFactoryBean bean) throws Exception {
        super(bean.getAddress(), component);
        init(bean);
    }

    public CxfRsSpringEndpoint(Component component, String uri, AbstractJAXRSFactoryBean bean) throws Exception {
        super(uri, component);
        setAddress(bean.getAddress());
        // Update the sfb address by resolving the properties
        bean.setAddress(getAddress());
        init(bean);
    }

    private void init(AbstractJAXRSFactoryBean bean) {
        this.bean = bean;
        if (bean instanceof BeanIdAware) {
            setBeanId(((BeanIdAware)bean).getBeanId());
        }

        ApplicationContext applicationContext = ((SpringCamelContext)getCamelContext()).getApplicationContext();
        configurer = new ConfigurerImpl(applicationContext);
    }

    @Override
    protected JAXRSServerFactoryBean newJAXRSServerFactoryBean() {
        checkBeanType(bean, JAXRSServerFactoryBean.class);
        return (JAXRSServerFactoryBean)bean;
    }

    @Override
    protected JAXRSClientFactoryBean newJAXRSClientFactoryBean() {
        return new SpringJAXRSClientFactoryBean();
    }

    @Override
    protected void setupJAXRSClientFactoryBean(JAXRSClientFactoryBean cfb, String address) {
        configurer.configureBean(beanId, cfb);
        cfb.setAddress(address);
        cfb.setThreadSafe(true);
    }

    public String getBeanId() {
        return beanId;
    }

    public void setBeanId(String id) {
        this.beanId = id;
    }
}
