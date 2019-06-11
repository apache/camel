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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.model.Model;
import org.apache.camel.spi.StreamCachingStrategy;

/**
 * To configure the {@link DefaultConfigurationProperties} on {@link org.apache.camel.CamelContext}
 * used by Camel Main, Camel Spring Boot and other runtimes.
 */
public final class DefaultConfigurationConfigurer {

    private DefaultConfigurationConfigurer() {
    }

    /**
     * Configures the {@link CamelContext} with the configuration.
     *
     * @param camelContext the camel context
     * @param config       the configuration
     */
    public static void configure(CamelContext camelContext, DefaultConfigurationProperties config) {
        if (!config.isJmxEnabled()) {
            camelContext.disableJMX();
        }

        if (config.getName() != null) {
            camelContext.adapt(ExtendedCamelContext.class).setName(config.getName());
        }

        if (config.getShutdownTimeout() > 0) {
            camelContext.getShutdownStrategy().setTimeout(config.getShutdownTimeout());
        }
        camelContext.getShutdownStrategy().setSuppressLoggingOnTimeout(config.isShutdownSuppressLoggingOnTimeout());
        camelContext.getShutdownStrategy().setShutdownNowOnTimeout(config.isShutdownNowOnTimeout());
        camelContext.getShutdownStrategy().setShutdownRoutesInReverseOrder(config.isShutdownRoutesInReverseOrder());
        camelContext.getShutdownStrategy().setLogInflightExchangesOnTimeout(config.isShutdownLogInflightExchangesOnTimeout());

        if (config.getLogDebugMaxChars() != 0) {
            camelContext.getGlobalOptions().put(Exchange.LOG_DEBUG_BODY_MAX_CHARS, "" + config.getLogDebugMaxChars());
        }

        // stream caching
        camelContext.setStreamCaching(config.isStreamCachingEnabled());
        camelContext.getStreamCachingStrategy().setAnySpoolRules(config.isStreamCachingAnySpoolRules());
        camelContext.getStreamCachingStrategy().setBufferSize(config.getStreamCachingBufferSize());
        camelContext.getStreamCachingStrategy().setRemoveSpoolDirectoryWhenStopping(config.isStreamCachingRemoveSpoolDirectoryWhenStopping());
        camelContext.getStreamCachingStrategy().setSpoolCipher(config.getStreamCachingSpoolCipher());
        if (config.getStreamCachingSpoolDirectory() != null) {
            camelContext.getStreamCachingStrategy().setSpoolDirectory(config.getStreamCachingSpoolDirectory());
        }
        if (config.getStreamCachingSpoolThreshold() != 0) {
            camelContext.getStreamCachingStrategy().setSpoolThreshold(config.getStreamCachingSpoolThreshold());
        }
        if (config.getStreamCachingSpoolUsedHeapMemoryLimit() != null) {
            StreamCachingStrategy.SpoolUsedHeapMemoryLimit limit;
            if ("Committed".equalsIgnoreCase(config.getStreamCachingSpoolUsedHeapMemoryLimit())) {
                limit = StreamCachingStrategy.SpoolUsedHeapMemoryLimit.Committed;
            } else if ("Max".equalsIgnoreCase(config.getStreamCachingSpoolUsedHeapMemoryLimit())) {
                limit = StreamCachingStrategy.SpoolUsedHeapMemoryLimit.Max;
            } else {
                throw new IllegalArgumentException("Invalid option " + config.getStreamCachingSpoolUsedHeapMemoryLimit() + " must either be Committed or Max");
            }
            camelContext.getStreamCachingStrategy().setSpoolUsedHeapMemoryLimit(limit);
        }
        if (config.getStreamCachingSpoolUsedHeapMemoryThreshold() != 0) {
            camelContext.getStreamCachingStrategy().setSpoolUsedHeapMemoryThreshold(config.getStreamCachingSpoolUsedHeapMemoryThreshold());
        }

        camelContext.setMessageHistory(config.isMessageHistory());
        camelContext.setLogMask(config.isLogMask());
        camelContext.setLogExhaustedMessageBody(config.isLogExhaustedMessageBody());
        camelContext.setHandleFault(config.isHandleFault());
        camelContext.setAutoStartup(config.isAutoStartup());
        camelContext.setAllowUseOriginalMessage(config.isAllowUseOriginalMessage());
        camelContext.setUseBreadcrumb(config.isUseBreadcrumb());
        camelContext.setUseDataType(config.isUseDataType());
        camelContext.setUseMDCLogging(config.isUseMdcLogging());
        camelContext.setLoadTypeConverters(config.isLoadTypeConverters());

        if (camelContext.getManagementStrategy().getManagementAgent() != null) {
            camelContext.getManagementStrategy().getManagementAgent().setEndpointRuntimeStatisticsEnabled(config.isEndpointRuntimeStatisticsEnabled());
            camelContext.getManagementStrategy().getManagementAgent().setStatisticsLevel(config.getJmxManagementStatisticsLevel());
            camelContext.getManagementStrategy().getManagementAgent().setManagementNamePattern(config.getJmxManagementNamePattern());
            camelContext.getManagementStrategy().getManagementAgent().setCreateConnector(config.isJmxCreateConnector());
        }

        camelContext.setTracing(config.isTracing());

        if (config.getThreadNamePattern() != null) {
            camelContext.getExecutorServiceManager().setThreadNamePattern(config.getThreadNamePattern());
        }

        if (config.getRouteFilterIncludePattern() != null || config.getRouteFilterExcludePattern() != null) {
            camelContext.getExtension(Model.class).setRouteFilterPattern(config.getRouteFilterIncludePattern(), config.getRouteFilterExcludePattern());
        }

    }
    
}
