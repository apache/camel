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
package org.apache.camel.component.ehcache.springboot.customizer;

import org.apache.camel.component.ehcache.EhcacheComponent;
import org.apache.camel.component.ehcache.springboot.EhcacheComponentAutoConfiguration;
import org.apache.camel.spi.ComponentCustomizer;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.ehcache.CacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * A simple implementation of {@link ComponentCustomizer} that auto discovers a
 * {@link CacheManager} instance and bind it to the {@link EhcacheComponent}
 * component.
 *
 * This configurer can be disabled either by disable all the
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@Configuration
@ConditionalOnProperty(name = "camel.component.ehcache.configurer.cache-manager.enabled", matchIfMissing = true)
@AutoConfigureAfter(CamelAutoConfiguration.class)
@AutoConfigureBefore(EhcacheComponentAutoConfiguration.class)
public class CacheManagerCustomizer extends AllNestedConditions implements ComponentCustomizer<EhcacheComponent> {
    @Autowired
    private CacheManager cacheManager;

    public CacheManagerCustomizer() {
        super(ConfigurationPhase.REGISTER_BEAN);
    }

    @Override
    public void customize(EhcacheComponent component) {
        component.setCacheManager(cacheManager);
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

    @ConditionalOnBean(CacheManager.class)
    static class OnCacheManager {
    }
    @ConditionalOnBean(CamelAutoConfiguration.class)
    static class OnCamelAutoConfiguration {
    }
}
