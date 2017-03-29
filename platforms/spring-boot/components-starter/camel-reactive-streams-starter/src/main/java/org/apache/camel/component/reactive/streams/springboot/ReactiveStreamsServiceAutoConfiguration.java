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
package org.apache.camel.component.reactive.streams.springboot;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.component.reactive.streams.ReactiveStreamsHelper;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.component.reactive.streams.engine.ReactiveStreamsEngineConfiguration;
import org.apache.camel.util.IntrospectionSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;


@AutoConfigureAfter(ReactiveStreamsComponentAutoConfiguration.class)
@ConditionalOnBean(ReactiveStreamsComponentAutoConfiguration.class)
@EnableConfigurationProperties(ReactiveStreamsComponentConfiguration.class)
public class ReactiveStreamsServiceAutoConfiguration {
    @Autowired
    private CamelContext context;
    @Autowired
    private ReactiveStreamsComponentConfiguration configuration;

    @Lazy
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(CamelContext.class)
    public CamelReactiveStreamsService camelReactiveStreamsService() throws Exception {
        ReactiveStreamsEngineConfiguration engineConfiguration = new ReactiveStreamsEngineConfiguration();

        if (configuration.getInternalEngineConfiguration() != null) {
            Map<String, Object> parameters = new HashMap<>();
            IntrospectionSupport.getProperties(configuration.getInternalEngineConfiguration(), parameters, null, false);
            IntrospectionSupport.setProperties(context, context.getTypeConverter(), engineConfiguration, parameters);
        }

        return ReactiveStreamsHelper.resolveReactiveStreamsService(context, configuration.getServiceType(), engineConfiguration);
    }

}
