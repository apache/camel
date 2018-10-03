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
package org.apache.camel.spring.boot.security;

import java.util.Collections;
import java.util.Map;

import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.support.jsse.GlobalSSLContextParametersSupplier;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration
@AutoConfigureBefore(CamelAutoConfiguration.class)
@EnableConfigurationProperties(CamelSSLConfigurationProperties.class)
@Conditional(CamelSSLAutoConfiguration.Condition.class)
public class CamelSSLAutoConfiguration {

    @Bean
    public GlobalSSLContextParametersSupplier sslContextParametersSupplier(CamelSSLConfigurationProperties properties) {
        final SSLContextParameters config = properties.getConfig() != null ? properties.getConfig() : new SSLContextParameters();
        return () -> config;
    }

    public static class Condition extends SpringBootCondition {
        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata annotatedTypeMetadata) {
            Binder binder = Binder.get(context.getEnvironment());
            Map<String, Object> sslProperties = binder.bind("camel.ssl.config", Bindable.mapOf(String.class, Object.class)).orElse(Collections.emptyMap());
            ConditionMessage.Builder message = ConditionMessage.forCondition("camel.ssl.config");
            if (sslProperties.size() > 0) {
                return ConditionOutcome.match(message.because("enabled"));
            }

            return ConditionOutcome.noMatch(message.because("not enabled"));
        }
    }

}
