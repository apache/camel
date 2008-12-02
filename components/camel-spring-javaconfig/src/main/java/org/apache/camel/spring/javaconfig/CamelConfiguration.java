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
package org.apache.camel.spring.javaconfig;

import org.apache.camel.Routes;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.CamelBeanPostProcessor;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.config.java.annotation.Bean;
import org.springframework.config.java.annotation.Configuration;

import java.util.List;

/**
 * A useful base class for writing
 * <a href="http://www.springsource.org/javaconfig">Spring JavaConfig</a>
 * configurations for working with Camel
 *
 * @version $Revision: 1.1 $
 */
@Configuration
public abstract class CamelConfiguration    {

    @Bean
    public CamelBeanPostProcessor camelBeanPostProcessor() throws Exception {
        return new CamelBeanPostProcessor(){
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                try {
                    SpringCamelContext context = (SpringCamelContext) getCamelContext();
                    if (context == null) {
                        // we have not yet injected the context so lets set it
                        setCamelContext(camelContext());
                    }
                    return super.postProcessAfterInitialization(bean, beanName);
                } catch (BeansException e) {
                    throw e;
                } catch (Exception e) {
                    throw new BeanInitializationException(e.getMessage(), e);
                }
            }
        };
    }

    /**
     * Returns the CamelContext
     */
    @Bean
    public SpringCamelContext camelContext() throws Exception {
        SpringCamelContext camelContext = new SpringCamelContext();
        List<RouteBuilder> routes = routes();
        for (Routes route : routes) {
            camelContext.addRoutes(route);
        }
        return camelContext;
    }


    /**
     * Returns the list of routes to use in this configuration
     */
    @Bean
    public abstract List<RouteBuilder> routes();

}
