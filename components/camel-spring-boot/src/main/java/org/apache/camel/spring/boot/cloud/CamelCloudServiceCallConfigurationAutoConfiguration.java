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
package org.apache.camel.spring.boot.cloud;

import org.apache.camel.CamelContext;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.cloud.ServiceCallDefinitionConstants;
import org.apache.camel.spi.Language;
import org.apache.camel.spring.boot.util.GroupCondition;
import org.apache.camel.util.ObjectHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@ConditionalOnBean({ CamelCloudAutoConfiguration.class, CamelContext.class})
@EnableConfigurationProperties(CamelCloudConfigurationProperties.class)
@Conditional(CamelCloudServiceCallConfigurationAutoConfiguration.ServiceCallCondition.class)
public class CamelCloudServiceCallConfigurationAutoConfiguration {
    @Autowired
    private CamelContext camelContext;
    @Autowired
    private CamelCloudConfigurationProperties configurationProperties;

    @Lazy
    @Bean(name = ServiceCallDefinitionConstants.DEFAULT_SERVICE_CALL_CONFIG_ID)
    @ConditionalOnMissingBean(name = ServiceCallDefinitionConstants.DEFAULT_SERVICE_CALL_CONFIG_ID)
    public ServiceCallConfigurationDefinition serviceCallConfiguration() throws Exception {
        ServiceCallConfigurationDefinition definition = new ServiceCallConfigurationDefinition();
        ObjectHelper.ifNotEmpty(configurationProperties.getServiceCall().getComponent(), definition::setComponent);
        ObjectHelper.ifNotEmpty(configurationProperties.getServiceCall().getUri(), definition::setUri);
        ObjectHelper.ifNotEmpty(configurationProperties.getServiceCall().getServiceDiscovery(), definition::setServiceDiscoveryRef);
        ObjectHelper.ifNotEmpty(configurationProperties.getServiceCall().getServiceFilter(), definition::setServiceFilterRef);
        ObjectHelper.ifNotEmpty(configurationProperties.getServiceCall().getServiceChooser(), definition::setServiceChooserRef);
        ObjectHelper.ifNotEmpty(configurationProperties.getServiceCall().getLoadBalancer(), definition::setLoadBalancerRef);

        String expression = configurationProperties.getServiceCall().getExpression();
        String expressionLanguage = configurationProperties.getServiceCall().getExpressionLanguage();

        if (ObjectHelper.isNotEmpty(expression) && ObjectHelper.isNotEmpty(expressionLanguage)) {
            Language language = camelContext.resolveLanguage(expressionLanguage);
            if (language == null) {
                throw new IllegalArgumentException("Unable to resolve language: " + expressionLanguage);
            }

            definition.setExpression(language.createExpression(expression));
        }

        return definition;
    }

    // *******************************
    // Condition
    // *******************************

    public static class ServiceCallCondition extends GroupCondition {
        public ServiceCallCondition() {
            super(
                "camel.cloud",
                "camel.cloud.service-call"
            );
        }
    }
}
