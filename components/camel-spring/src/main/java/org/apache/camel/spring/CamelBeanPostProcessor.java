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
package org.apache.camel.spring;

import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.core.xml.CamelJMXAgentDefinition;
import org.apache.camel.impl.CamelPostProcessorHelper;
import org.apache.camel.impl.DefaultCamelBeanPostProcessor;
import org.apache.camel.spi.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Spring specific {@link DefaultCamelBeanPostProcessor} which uses Spring {@link BeanPostProcessor} to post process beans.
 *
 * @see DefaultCamelBeanPostProcessor
 */
@Metadata(label = "spring,configuration")
@XmlRootElement(name = "beanPostProcessor")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {
    private static final Logger LOG = LoggerFactory.getLogger(CamelBeanPostProcessor.class);
    @XmlTransient
    private CamelContext camelContext;
    @XmlTransient
    private ApplicationContext applicationContext;
    @XmlTransient
    private String camelId;

    // must use a delegate, as we cannot extend DefaultCamelBeanPostProcessor, as this will cause the
    // XSD schema generator to include the DefaultCamelBeanPostProcessor as a type, which we do not want to
    @XmlTransient
    private final DefaultCamelBeanPostProcessor delegate = new DefaultCamelBeanPostProcessor() {
        @Override
        public CamelContext getOrLookupCamelContext() {
            if (camelContext == null) {
                if (camelId != null) {
                    LOG.trace("Looking up CamelContext by id: {} from Spring ApplicationContext: {}", camelId, applicationContext);
                    camelContext = applicationContext.getBean(camelId, CamelContext.class);
                } else {
                    // lookup by type and grab the single CamelContext if exists
                    LOG.trace("Looking up CamelContext by type from Spring ApplicationContext: {}", applicationContext);
                    Map<String, CamelContext> contexts = applicationContext.getBeansOfType(CamelContext.class);
                    if (contexts != null && contexts.size() == 1) {
                        camelContext = contexts.values().iterator().next();
                    }
                }
            }
            return camelContext;
        }

        @Override
        public boolean canPostProcessBean(Object bean, String beanName) {
            // the JMXAgent is a bit strange and causes Spring issues if we let it being
            // post processed by this one. It does not need it anyway so we are good to go.
            // We should also avoid to process the null object bean (in Spring 2.5.x)
            if (bean == null || bean instanceof CamelJMXAgentDefinition) {
                return false;
            }

            return super.canPostProcessBean(bean, beanName);
        }

        @Override
        public CamelPostProcessorHelper getPostProcessorHelper() {
            // lets lazily create the post processor
            if (camelPostProcessorHelper == null) {
                camelPostProcessorHelper = new CamelPostProcessorHelper() {

                    @Override
                    public CamelContext getCamelContext() {
                        // lets lazily lookup the camel context here
                        // as doing this will cause this context to be started immediately
                        // breaking the lifecycle ordering of different camel contexts
                        // so we only want to do this on demand
                        return delegate.getOrLookupCamelContext();
                    }

                    @Override
                    protected RuntimeException createProxyInstantiationRuntimeException(Class<?> type, Endpoint endpoint, Exception e) {
                        return new BeanInstantiationException(type, "Could not instantiate proxy of type " + type.getName() + " on endpoint " + endpoint, e);
                    }

                    protected boolean isSingleton(Object bean, String beanName) {
                        // no application context has been injected which means the bean
                        // has not been enlisted in Spring application context
                        if (applicationContext == null || beanName == null) {
                            return super.isSingleton(bean, beanName);
                        } else {
                            return applicationContext.isSingleton(beanName);
                        }
                    }
                };
            }
            return camelPostProcessorHelper;
        }
    };

    public CamelBeanPostProcessor() {
    }

    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        try {
            return delegate.postProcessBeforeInitialization(bean, beanName);
        } catch (Exception e) {
            // do not wrap already beans exceptions
            if (e instanceof BeansException) {
                throw (BeansException) e;
            }
            throw new GenericBeansException("Error post processing bean: " + beanName, e);
        }
    }

    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        try {
            return delegate.postProcessAfterInitialization(bean, beanName);
        } catch (Exception e) {
            // do not wrap already beans exceptions
            if (e instanceof BeansException) {
                throw (BeansException) e;
            }
            throw new GenericBeansException("Error post processing bean: " + beanName, e);
        }
    }

    // Properties
    // -------------------------------------------------------------------------

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getCamelId() {
        return camelId;
    }

    public void setCamelId(String camelId) {
        this.camelId = camelId;
    }

}
