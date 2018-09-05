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
package org.apache.camel.component.hazelcast.ringbuffer.springboot.customizer;

import org.apache.camel.component.hazelcast.ringbuffer.HazelcastRingbufferComponent;
import org.apache.camel.component.hazelcast.ringbuffer.springboot.HazelcastRingbufferComponentAutoConfiguration;
import org.apache.camel.component.hazelcast.springboot.customizer.AbstractHazelcastInstanceCustomizer;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Order(Ordered.LOWEST_PRECEDENCE)
@Configuration
@Conditional(HazelcastInstanceCustomizer.NestedConditions.class)
@AutoConfigureAfter(CamelAutoConfiguration.class)
@AutoConfigureBefore(HazelcastRingbufferComponentAutoConfiguration.class)
@EnableConfigurationProperties(HazelcastInstanceCustomizerConfiguration.class)
public class HazelcastInstanceCustomizer extends AbstractHazelcastInstanceCustomizer<HazelcastRingbufferComponent, HazelcastInstanceCustomizerConfiguration> {
    @Override
    public String getId() {
        return "camel.component.hazelcast-ringbuffer.customizer.hazelcast-instance";
    }
}
