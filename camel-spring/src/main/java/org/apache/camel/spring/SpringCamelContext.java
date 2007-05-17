/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spring;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.CamelContext;
import org.apache.camel.spring.spi.SpringComponentResolver;
import org.apache.camel.spring.spi.SpringInjector;
import org.apache.camel.spring.component.BeanComponent;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.AbstractRefreshableApplicationContext;

/**
 * A Spring aware implementation of {@link CamelContext} which will automatically register itself with Springs lifecycle
 * methods  plus allows spring to be used to customize a any
 * <a href="http://activemq.apache.org/camel/type-converter.html">Type Converters</a> as well as supporting accessing components
 * and beans via the Spring {@link ApplicationContext}
 *
 * @version $Revision$
 */
public class SpringCamelContext extends DefaultCamelContext implements InitializingBean, DisposableBean, ApplicationContextAware {
    private ApplicationContext applicationContext;

    public SpringCamelContext() {
    }

    public SpringCamelContext(ApplicationContext applicationContext) {
        setApplicationContext(applicationContext);
    }

    public static SpringCamelContext springCamelContext(ApplicationContext applicationContext) throws Exception {
        // lets try and look up a configured camel context in the context
        String[] names = applicationContext.getBeanNamesForType(SpringCamelContext.class);
        if (names.length == 1) {
            return (SpringCamelContext) applicationContext.getBean(names[0], SpringCamelContext.class);
        }
        SpringCamelContext answer = new SpringCamelContext();
        answer.setApplicationContext(applicationContext);
        answer.afterPropertiesSet();
        return answer;
    }

    public static SpringCamelContext springCamelContext(String configLocations) throws Exception {
        return springCamelContext(new ClassPathXmlApplicationContext(configLocations));
    }

    public void afterPropertiesSet() throws Exception {
        // lets force lazy initialisation
        getInjector();

        start();
    }

    public void destroy() throws Exception {
        stop();
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        addComponent("bean", new BeanComponent(applicationContext));
    }

    @Override
    protected Injector createInjector() {
        return new SpringInjector((AbstractRefreshableApplicationContext) getApplicationContext());
    }

    @Override
    protected ComponentResolver createComponentResolver() {
        ComponentResolver defaultResolver = super.createComponentResolver();
        return new SpringComponentResolver(getApplicationContext(), defaultResolver);
    }
}
