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
import org.infinispan.manager.EmbeddedCacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Order(101)
@Configuration
@ConditionalOnBean({ EmbeddedCacheManager.class, CamelAutoConfiguration.class })
@AutoConfigureAfter({ CamelAutoConfiguration.class, CacheAutoConfiguration.class })
@AutoConfigureBefore(InfinispanComponentAutoConfiguration.class)
@EnableConfigurationProperties(EmbeddedCacheManagerCustomizerConfiguration.class)
public class EmbeddedCacheManagerCustomizer implements HasId, ComponentCustomizer<InfinispanComponent> {
    @Autowired
    private EmbeddedCacheManager cacheManager;
    @Autowired
    private EmbeddedCacheManagerCustomizerConfiguration configuration;

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
        return "camel.component.infinispan.customizer.embedded-cache-manager";
    }
}
