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
package org.apache.camel.component.kubernetes.springboot.cloud;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.component.kubernetes.cloud.KubernetesServiceDiscoveryFactory;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.util.IntrospectionSupport;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.bind.RelaxedPropertyResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration
@ConditionalOnBean(CamelAutoConfiguration.class)
@Conditional(KubernetesCloudAutoConfiguration.Condition.class)
@AutoConfigureAfter(CamelAutoConfiguration.class)
@EnableConfigurationProperties(KubernetesCloudConfiguration.class)
public class KubernetesCloudAutoConfiguration {
    @Lazy
    @Bean(name = "kubernetes-service-discovery")
    @ConditionalOnClass(CamelContext.class)
    public ServiceDiscovery configureServiceDiscoveryFactory(CamelContext camelContext, KubernetesCloudConfiguration configuration) throws Exception {
        KubernetesServiceDiscoveryFactory factory = new KubernetesServiceDiscoveryFactory();

        Map<String, Object> parameters = new HashMap<>();
        IntrospectionSupport.getProperties(configuration, parameters, null, false);
        IntrospectionSupport.setProperties(camelContext, camelContext.getTypeConverter(), factory, parameters);

        return factory.newInstance(camelContext);
    }

    public static class Condition extends SpringBootCondition {
        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext conditionContext, AnnotatedTypeMetadata annotatedTypeMetadata) {
            boolean groupEnabled = isEnabled(conditionContext, "camel.cloud.", true);

            ConditionMessage.Builder message = ConditionMessage.forCondition("camel.cloud.kubernetes");
            if (isEnabled(conditionContext, "camel.cloud.kubernetes.", groupEnabled)) {
                return ConditionOutcome.match(message.because("enabled"));
            }

            return ConditionOutcome.noMatch(message.because("not enabled"));
        }

        private boolean isEnabled(ConditionContext context, String prefix, boolean defaultValue) {
            RelaxedPropertyResolver resolver = new RelaxedPropertyResolver(context.getEnvironment(), prefix);
            return resolver.getProperty("enabled", Boolean.class, defaultValue);
        }
    }
}
