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
package org.apache.camel.component.hazelcast.springboot.customizer;

import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.component.hazelcast.HazelcastDefaultComponent;
import org.apache.camel.spi.ComponentCustomizer;
import org.apache.camel.spi.HasId;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

public abstract class AbstractHazelcastInstanceCustomizer<T extends HazelcastDefaultComponent, C extends AbstractHazelcastInstanceCustomizerConfiguration>
        implements HasId, ComponentCustomizer<T> {

    @Autowired
    private HazelcastInstance hazelcastInstance;
    @Autowired
    private C configuration;

    @Override
    public void customize(T component) {
        // Set the cache manager only if the customizer is configured to always
        // set it or if no cache manager is already configured on component
        if (configuration.isOverride() || component.getHazelcastInstance() == null) {
            component.setHazelcastInstance(hazelcastInstance);
        }
    }

    // *************************************************************************
    // By default ConditionalOnBean works using an OR operation so if you list
    // a number of classes, the condition succeeds if a single instance of any
    // class is found.
    //
    // A workaround is to use AllNestedConditions and creates some dummy classes
    // annotated with @ConditionalOnBean
    //
    // This should be fixed in spring-boot 2.0 where ConditionalOnBean uses and
    // AND operation instead of the OR as it does today.
    // *************************************************************************

    public static class NestedConditions extends AllNestedConditions {
        public NestedConditions() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnBean(HazelcastInstance.class)
        static class OnHazelcastInstance {
        }

        @ConditionalOnBean(CamelAutoConfiguration.class)
        static class OnCamelAutoConfiguration {
        }
    }
}
