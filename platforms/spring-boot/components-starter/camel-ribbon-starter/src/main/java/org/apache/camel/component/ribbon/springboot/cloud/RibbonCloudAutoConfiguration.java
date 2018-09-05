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
package org.apache.camel.component.ribbon.springboot.cloud;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceLoadBalancer;
import org.apache.camel.component.ribbon.cloud.RibbonServiceLoadBalancerFactory;
import org.apache.camel.model.cloud.springboot.RibbonServiceCallServiceLoadBalancerConfigurationCommon;
import org.apache.camel.model.cloud.springboot.RibbonServiceCallServiceLoadBalancerConfigurationProperties;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.spring.boot.util.GroupCondition;
import org.apache.camel.util.IntrospectionSupport;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@ConditionalOnBean(CamelAutoConfiguration.class)
@Conditional(RibbonCloudAutoConfiguration.Condition.class)
@AutoConfigureAfter(CamelAutoConfiguration.class)
@EnableConfigurationProperties(RibbonServiceCallServiceLoadBalancerConfigurationProperties.class)
public class RibbonCloudAutoConfiguration {
    @Autowired
    private CamelContext camelContext;
    @Autowired
    private RibbonServiceCallServiceLoadBalancerConfigurationProperties configuration;
    @Autowired
    private ConfigurableBeanFactory beanFactory;

    @Lazy
    @Bean(name = "ribbon-load-balancer")
    @ConditionalOnClass(CamelContext.class)
    public ServiceLoadBalancer configureLoadBalancerFactory() throws Exception {
        RibbonServiceLoadBalancerFactory factory = new RibbonServiceLoadBalancerFactory();

        IntrospectionSupport.setProperties(
            camelContext,
            camelContext.getTypeConverter(),
            factory,
            IntrospectionSupport.getNonNullProperties(configuration));

        return factory.newInstance(camelContext);
    }

    @PostConstruct
    public void postConstruct() {
        if (beanFactory != null) {
            Map<String, Object> parameters = new HashMap<>();

            for (Map.Entry<String, RibbonServiceCallServiceLoadBalancerConfigurationCommon> entry : configuration.getConfigurations().entrySet()) {
                // clean up params
                parameters.clear();

                // The instance factory
                RibbonServiceLoadBalancerFactory factory = new RibbonServiceLoadBalancerFactory();

                try {
                    IntrospectionSupport.getProperties(entry.getValue(), parameters, null, false);
                    IntrospectionSupport.setProperties(camelContext, camelContext.getTypeConverter(), factory, parameters);

                    beanFactory.registerSingleton(entry.getKey(), factory.newInstance(camelContext));
                } catch (Exception e) {
                    throw new BeanCreationException(entry.getKey(), e.getMessage(), e);
                }
            }
        }
    }

    // *******************************
    // Condition
    // *******************************

    public static class Condition extends GroupCondition {
        public Condition() {
            super(
                "camel.cloud.ribbon",
                "camel.cloud.ribbon.load-balancer"
            );
        }
    }
}
