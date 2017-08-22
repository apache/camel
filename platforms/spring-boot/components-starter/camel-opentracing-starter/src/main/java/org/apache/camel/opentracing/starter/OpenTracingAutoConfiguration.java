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
package org.apache.camel.opentracing.starter;

import io.opentracing.Tracer;

import org.apache.camel.CamelContext;
import org.apache.camel.opentracing.OpenTracingTracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenTracingConfigurationProperties.class)
@ConditionalOnProperty(value = "camel.opentracing.enabled", matchIfMissing = true)
public class OpenTracingAutoConfiguration {

    @Autowired(required = false)
    private Tracer tracer;

    @Bean(initMethod = "", destroyMethod = "")
    // Camel handles the lifecycle of this bean
    @ConditionalOnMissingBean(OpenTracingTracer.class)
    OpenTracingTracer openTracingEventNotifier(CamelContext camelContext,
                        OpenTracingConfigurationProperties config) {
        OpenTracingTracer ottracer = new OpenTracingTracer();
        if (tracer != null) {
            ottracer.setTracer(tracer);
        }
        if (config.getExcludePatterns() != null) {
            ottracer.setExcludePatterns(config.getExcludePatterns());
        }
        ottracer.init(camelContext);

        return ottracer;
    }

}
