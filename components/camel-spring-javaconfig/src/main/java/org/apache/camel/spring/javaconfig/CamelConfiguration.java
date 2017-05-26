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
package org.apache.camel.spring.javaconfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.CamelBeanPostProcessor;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A useful base class for writing
 * <a
 * href="http://docs.spring.io/spring/docs/current/spring-framework-reference/html/beans.html#beans-annotation-config">
 * Spring annotation-based</a> configurations for working with Camel.
 */
@Configuration
public abstract class CamelConfiguration implements BeanFactoryAware, ApplicationContextAware {
    
    private BeanFactory beanFactory;
    private AutowireCapableBeanFactory autowireCapableBeanFactory;
    private ApplicationContext applicationContext;

    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        if (beanFactory instanceof AutowireCapableBeanFactory) {
            autowireCapableBeanFactory = (AutowireCapableBeanFactory) beanFactory;
        }
    }

    protected BeanFactory getBeanFactory() {
        return this.beanFactory;
    }

    public void setApplicationContext(ApplicationContext ac) {
        this.applicationContext = ac;
    }

    protected ApplicationContext getApplicationContext() {
        return this.applicationContext;
    }

    public Object getBean(String beanName) {
        return beanFactory.getBean(beanName);
    }

    public <T> T getBean(Class<T> type) {
        return beanFactory.getBean(type);
    }

    public <T> T getBean(String beanName, Class<T> type) {
        return beanFactory.getBean(beanName, type);
    }

    /**
     * Invoke callbacks on the object, as though it were configured in the factory. If appropriate,
     * the object may be wrapped before being returned. For this reason, it is recommended to always
     * respect the return value when using this method.
     *
     * @param   object  object to configure
     *
     * @return  either the original object or a wrapped one after callbacks called on it.
     */
    protected <T> T getConfigured(T object) {
        if (this.autowireCapableBeanFactory == null) {
            throw new UnsupportedOperationException(
                "Cannot configure object - not running in an AutowireCapableBeanFactory");
        }

        @SuppressWarnings("unchecked") // See SPR-4955
        T configuredObject = (T) autowireCapableBeanFactory.initializeBean(object, null);

        // this block copied from ApplicationContextAwareProcessor.  See SJC-149.
        if (this.applicationContext != null) {
            if (configuredObject instanceof ResourceLoaderAware) {
                ((ResourceLoaderAware) configuredObject).setResourceLoader(this.applicationContext);
            }

            if (configuredObject instanceof ApplicationEventPublisherAware) {
                ((ApplicationEventPublisherAware) configuredObject).setApplicationEventPublisher(this.applicationContext);
            }

            if (configuredObject instanceof MessageSourceAware) {
                ((MessageSourceAware) configuredObject).setMessageSource(this.applicationContext);
            }

            if (configuredObject instanceof ApplicationContextAware) {
                ((ApplicationContextAware) configuredObject).setApplicationContext(this.applicationContext);
            }
        }

        return configuredObject;
    }

    /**
     * Get's the {@link ProducerTemplate} to be used.
     */
    @Bean(initMethod = "", destroyMethod = "")
    // Camel handles the lifecycle of this bean
    public ProducerTemplate producerTemplate(CamelContext camelContext) throws Exception {
        return camelContext.createProducerTemplate();
    }

    /**
     * Get's the {@link ConsumerTemplate} to be used.
     */
    @Bean(initMethod = "", destroyMethod = "")
    // Camel handles the lifecycle of this bean
    public ConsumerTemplate consumerTemplate(CamelContext camelContext) throws Exception {
        return camelContext.createConsumerTemplate();
    }

    /**
     * Camel post processor - required to support Camel annotations.
     */
    @Bean
    public CamelBeanPostProcessor camelBeanPostProcessor() throws Exception {
        CamelBeanPostProcessor answer = new CamelBeanPostProcessor();
        answer.setApplicationContext(getApplicationContext());
        // do not set CamelContext as we will lazy evaluate that later
        return answer;
    }

    /**
     * Get's the {@link CamelContext} to be used.
     */
    @Bean
    public CamelContext camelContext() throws Exception {
        CamelContext camelContext = createCamelContext();
        setupCamelContext(camelContext);
        return camelContext;
    }

    @Bean
    RoutesCollector routesCollector(ApplicationContext applicationContext) {
        return new RoutesCollector(applicationContext, this);
    }

    /**
     * Callback to setup {@link CamelContext} before its started
     */
    protected void setupCamelContext(CamelContext camelContext) throws Exception {
        // noop
    }

    /**
     * Factory method returning {@link CamelContext} used by this configuration.
     *
     * @return {@link CamelContext} used by this configuration. By default {@link SpringCamelContext} instance is
     * created, to fully integrate Spring application context and Camel registry.
     */
    protected CamelContext createCamelContext() throws Exception {
        return new SpringCamelContext(getApplicationContext());
    }

    /**
     * Returns the list of routes to use in this configuration. By default autowires all
     * {@link org.apache.camel.builder.RouteBuilder} instances available in the
     * {@link org.springframework.context.ApplicationContext}.
     */
    public List<RouteBuilder> routes() {
        if (this.applicationContext != null) {
            Map<String, RouteBuilder> routeBuildersMap = applicationContext.getBeansOfType(RouteBuilder.class);
            List<RouteBuilder> routeBuilders = new ArrayList<RouteBuilder>(routeBuildersMap.size());
            for (RouteBuilder routeBuilder : routeBuildersMap.values()) {
                routeBuilders.add(routeBuilder);
            }
            return routeBuilders;
        } else {
            return emptyList();
        }
    }

}
