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

import org.apache.camel.CamelContext;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.cxf.configuration.spring.ConfigurerImpl;
import org.apache.cxf.jaxrs.AbstractJAXRSFactoryBean;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * 
 */
public class CxfRsSpringEndpoint extends CxfRsEndpoint implements BeanIdAware {
    private AbstractJAXRSFactoryBean bean;
    private ApplicationContext applicationContext;
    private String beanId;
    private ConfigurerImpl configurer;
    
    public CxfRsSpringEndpoint(CamelContext context, AbstractJAXRSFactoryBean bean) throws Exception {
        super(bean.getAddress(), context);        
        init(bean);
    }
    
    private void init(AbstractJAXRSFactoryBean bean) {
        this.bean = bean;
        if (bean instanceof BeanIdAware) {
            setBeanId(((BeanIdAware)bean).getBeanId());
        }
        applicationContext = ((SpringCamelContext)getCamelContext()).getApplicationContext();
        // create configurer
        configurer = new ConfigurerImpl(applicationContext);
    }
    
    void configure(Object beanInstance) {
        // check the ApplicationContext states first , and call the refresh if necessary
        if (applicationContext instanceof AbstractApplicationContext) {
            AbstractApplicationContext context = (AbstractApplicationContext) applicationContext;
            if (!context.isActive()) {
                context.refresh();
            }
        }
        configurer.configureBean(beanId, beanInstance);
    }
    
    void checkBeanType(Class<?> clazz) {
        if (!clazz.isAssignableFrom(bean.getClass())) {
            throw new IllegalArgumentException("The configure bean is not the instance of " + clazz.getName());
        }
    }
    
    @Override
    protected void setupJAXRSServerFactoryBean(JAXRSServerFactoryBean sfb) {
        checkBeanType(JAXRSServerFactoryBean.class);
        configure(sfb);
        
    }
    
    @Override
    protected void setupJAXRSClientFactoryBean(JAXRSClientFactoryBean cfb, String address) {
        checkBeanType(JAXRSClientFactoryBean.class);
        configure(cfb);      
        cfb.setAddress(address);
    }
    
    public String getBeanId() {
        return beanId;
    }
    
    public void setBeanId(String id) {        
        this.beanId = id;
    }
}
