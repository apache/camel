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

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.model.FaultToleranceConfigurationDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.spi.VariableRepository;
import org.apache.camel.spi.VariableRepositoryFactory;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.OrderedLocationProperties;
import org.apache.camel.util.PropertiesHelper;
import org.apache.camel.util.StringHelper;
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
            OrderedLocationProperties autoConfiguredProperties,
            OrderedLocationProperties resilience4jProperties,
            OrderedLocationProperties faultToleranceProperties)
            throws Exception {

        ModelCamelContext model = (ModelCamelContext) camelContext;

        if (!resilience4jProperties.isEmpty() || mainConfigurationProperties.hasResilience4jConfiguration()) {
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

        if (!faultToleranceProperties.isEmpty() || mainConfigurationProperties.hasFaultToleranceConfiguration()) {
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

    static void setVariableProperties(
            CamelContext camelContext,
            OrderedLocationProperties variableProperties,
            OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        for (String key : variableProperties.stringPropertyNames()) {
            String value = variableProperties.getProperty(key);
            String id = "global";
            if (key.startsWith("route.")) {
                id = "route";
                key = key.substring(6);
                key = StringHelper.replaceFirst(key, ".", ":");
            } else if (key.startsWith("global.")) {
                id = "global";
                key = key.substring(7);
                key = StringHelper.replaceFirst(key, ".", ":");
            }
            VariableRepository repo = camelContext.getCamelContextExtension().getContextPlugin(VariableRepositoryFactory.class)
                    .getVariableRepository(id);
            // it may be a resource to load from disk then
            if (value.startsWith(LanguageSupport.RESOURCE)) {
                value = value.substring(9);
                if (ResourceHelper.hasScheme(value)) {
                    InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, value);
                    value = IOHelper.loadText(is);
                    IOHelper.close(is);
                }
            }
            repo.setVariable(key, value);
        }
        for (var e : variableProperties.entrySet()) {
            String loc = variableProperties.getLocation(e.getKey());
            autoConfiguredProperties.put(loc, "camel.variable." + e.getKey(), e.getValue());
        }
    }

    static void setThreadPoolProperties(
            CamelContext camelContext,
            MainConfigurationProperties mainConfigurationProperties,
            OrderedLocationProperties threadPoolProperties,
            OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        ThreadPoolConfigurationProperties tp = mainConfigurationProperties.threadPool();

        // extract all config to know their parent ids so we can set the values afterwards
        Map<String, Object> hcConfig = PropertiesHelper.extractProperties(threadPoolProperties.asMap(), "config", false);
        Map<String, ThreadPoolProfileConfigurationProperties> tpConfigs = new HashMap<>();
        // build set of configuration objects
        for (Map.Entry<String, Object> entry : hcConfig.entrySet()) {
            String id = StringHelper.between(entry.getKey(), "[", "]");
            if (id != null) {
                ThreadPoolProfileConfigurationProperties tcp = tpConfigs.get(id);
                if (tcp == null) {
                    tcp = new ThreadPoolProfileConfigurationProperties();
                    tcp.setId(id);
                    tpConfigs.put(id, tcp);
                }
            }
        }
        if (tp.getConfig() != null) {
            tp.getConfig().putAll(tpConfigs);
        } else {
            tp.setConfig(tpConfigs);
        }

        setPropertiesOnTarget(camelContext, tp, threadPoolProperties, "camel.threadpool.",
                mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);

        // okay we have all properties set so we should be able to create thread pool profiles and register them on camel
        final ThreadPoolProfile dp = new ThreadPoolProfileBuilder("default")
                .poolSize(tp.getPoolSize())
                .maxPoolSize(tp.getMaxPoolSize())
                .keepAliveTime(tp.getKeepAliveTime(), tp.getTimeUnit())
                .maxQueueSize(tp.getMaxQueueSize())
                .allowCoreThreadTimeOut(tp.getAllowCoreThreadTimeOut())
                .rejectedPolicy(tp.getRejectedPolicy()).build();

        for (ThreadPoolProfileConfigurationProperties config : tp.getConfig().values()) {
            ThreadPoolProfileBuilder builder = new ThreadPoolProfileBuilder(config.getId(), dp);
            final ThreadPoolProfile tpp = builder.poolSize(config.getPoolSize())
                    .maxPoolSize(config.getMaxPoolSize())
                    .keepAliveTime(config.getKeepAliveTime(), config.getTimeUnit())
                    .maxQueueSize(config.getMaxQueueSize())
                    .allowCoreThreadTimeOut(config.getAllowCoreThreadTimeOut())
                    .rejectedPolicy(config.getRejectedPolicy()).build();
            if (!tpp.isEmpty()) {
                camelContext.getExecutorServiceManager().registerThreadPoolProfile(tpp);
            }
        }

        if (!dp.isEmpty()) {
            dp.setDefaultProfile(true);
            camelContext.getExecutorServiceManager().setDefaultThreadPoolProfile(dp);
        }
    }

}
