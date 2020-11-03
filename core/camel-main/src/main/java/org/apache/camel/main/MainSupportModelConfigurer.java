/*
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
package org.apache.camel.main;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.model.FaultToleranceConfigurationDefinition;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.main.MainHelper.setPropertiesOnTarget;

/**
 * Used for configuring that requires access to the model.
 */
public final class MainSupportModelConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(MainSupportModelConfigurer.class);

    private MainSupportModelConfigurer() {
    }

    static void configureModelCamelContext(
            CamelContext camelContext,
            MainConfigurationProperties mainConfigurationProperties,
            Map<String, String> autoConfiguredProperties,
            Map<String, Object> hystrixProperties,
            Map<String, Object> resilience4jProperties,
            Map<String, Object> faultToleranceProperties)
            throws Exception {

        ModelCamelContext model = camelContext.adapt(ModelCamelContext.class);

        if (!hystrixProperties.isEmpty()) {
            HystrixConfigurationProperties hystrix = mainConfigurationProperties.hystrix();
            LOG.debug("Auto-configuring Hystrix Circuit Breaker EIP from loaded properties: {}", hystrixProperties.size());
            setPropertiesOnTarget(camelContext, hystrix, hystrixProperties, "camel.hystrix.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
            HystrixConfigurationDefinition hystrixModel = model.getHystrixConfiguration(null);
            if (hystrixModel == null) {
                hystrixModel = new HystrixConfigurationDefinition();
                model.setHystrixConfiguration(hystrixModel);
            }
            if (hystrix != null) {
                setPropertiesOnTarget(camelContext, hystrixModel, hystrix);
            }
        }

        if (!resilience4jProperties.isEmpty()) {
            Resilience4jConfigurationProperties resilience4j = mainConfigurationProperties.resilience4j();
            LOG.debug("Auto-configuring Resilience4j Circuit Breaker EIP from loaded properties: {}",
                    resilience4jProperties.size());
            setPropertiesOnTarget(camelContext, resilience4j, resilience4jProperties, "camel.resilience4j.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
            Resilience4jConfigurationDefinition resilience4jModel = model.getResilience4jConfiguration(null);
            if (resilience4jModel == null) {
                resilience4jModel = new Resilience4jConfigurationDefinition();
                model.setResilience4jConfiguration(resilience4jModel);
            }
            setPropertiesOnTarget(camelContext, resilience4jModel, resilience4j);
        }

        if (!faultToleranceProperties.isEmpty()) {
            FaultToleranceConfigurationProperties faultTolerance = mainConfigurationProperties.faultTolerance();
            LOG.debug("Auto-configuring MicroProfile Fault Tolerance Circuit Breaker EIP from loaded properties: {}",
                    faultToleranceProperties.size());
            setPropertiesOnTarget(camelContext, faultTolerance, faultToleranceProperties, "camel.faulttolerance.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
            FaultToleranceConfigurationDefinition faultToleranceModel = model.getFaultToleranceConfiguration(null);
            if (faultToleranceModel == null) {
                faultToleranceModel = new FaultToleranceConfigurationDefinition();
                model.setFaultToleranceConfiguration(faultToleranceModel);
            }
            setPropertiesOnTarget(camelContext, faultToleranceModel, faultTolerance);
        }
    }

}
