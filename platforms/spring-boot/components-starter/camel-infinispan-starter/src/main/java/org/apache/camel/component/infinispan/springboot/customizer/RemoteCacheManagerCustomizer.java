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
package org.apache.camel.component.infinispan.springboot.customizer;

import org.apache.camel.component.infinispan.InfinispanComponent;
import org.apache.camel.component.infinispan.springboot.InfinispanComponentAutoConfiguration;
import org.apache.camel.spi.ComponentCustomizer;
import org.apache.camel.spi.HasId;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Order(100)
@Configuration
@Conditional(RemoteCacheManagerCustomizer.Conditions.class)
@AutoConfigureAfter(CamelAutoConfiguration.class)
@AutoConfigureBefore(InfinispanComponentAutoConfiguration.class)
@EnableConfigurationProperties(RemoteCacheManagerCustomizerConfiguration.class)
public class RemoteCacheManagerCustomizer implements HasId, ComponentCustomizer<InfinispanComponent> {
    @Autowired
    private RemoteCacheManager cacheManager;
    @Autowired
    private RemoteCacheManagerCustomizerConfiguration configuration;

    @Override
    public void customize(InfinispanComponent component) {
        // Set the cache manager only if the customizer is configured to always
        // set it or if no cache manager is already configured on component
        if (configuration.isOverride() || component.getCacheContainer() == null) {
            component.setCacheContainer(cacheManager);
        }
    }

    @Override
    public String getId() {
        return "camel.component.infinispan.customizer.remote-cache-manager";
    }

    // *************************************************************************
    // By default ConditionalOnBean works using an OR operation so if you list
    // a number of classes, the condition succeeds if a single instance of the
    // classes is found.
    //
    // A workaround is to use AllNestedConditions and creates some dummy classes
    // annotated @ConditionalOnBean
    //
    // This should be fixed in spring-boot 2.0 where ConditionalOnBean uses and
    // AND operation instead of the OR as it does today.
    // *************************************************************************

    static class Conditions extends AllNestedConditions {
        public Conditions() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnBean(RemoteCacheManager.class)
        static class OnCacheManager {
        }

        @ConditionalOnBean(CamelAutoConfiguration.class)
        static class OnCamelAutoConfiguration {
        }
    }
}
