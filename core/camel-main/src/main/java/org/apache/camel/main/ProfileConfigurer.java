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

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ManagementStatisticsLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configure Camel Main with the chosen profile.
 *
 * This is for Camel CLI and Standalone Camel, not Spring Boot or Quarkus; as they have their own profile concept.
 */
public class ProfileConfigurer {

    protected static final Logger LOG = LoggerFactory.getLogger(ProfileConfigurer.class);

    /**
     * Configures camel-main to run in given profile
     *
     * @param camelContext   the camel context
     * @param profile        the profile
     * @param config         the main configuration
     * @param autoConfigured properties that were explicitly configured by the user (may be null)
     */
    public static void configureMain(
            CamelContext camelContext, String profile, MainConfigurationProperties config, Properties autoConfigured) {
        if (profile == null || profile.isBlank()) {
            // no profile is active
            return;
        }

        if ("dev".equals(profile)) {
            // make tracing at least standby so we can use it in dev-mode
            boolean enabled = config.tracerConfig().isEnabled();
            if (!enabled) {
                config.tracerConfig().withStandby(true);
            }
            if (!config.isTracing()) {
                config.setTracingStandby(true);
            }
            // enable error registry to capture routing errors
            config.errorRegistryConfig().withEnabled(true);
        }

        if ("dev".equals(profile)) {
            // dev profile allows insecure:dev options by default since those features
            // (devConsole, upload, etc.) are expected in development.
            // Users can still override this explicitly via camel.security.insecureDevPolicy=warn/fail.
            if (config.securityConfig().getInsecureDevPolicy() == null) {
                config.securityConfig().setInsecureDevPolicy("allow");
            }
        }

        if ("prod".equals(profile)) {
            // production profile defaults security policy to "fail" so that insecure
            // configurations prevent startup. Users can still override this explicitly
            // via camel.security.policy=warn (or allow) in their properties.
            config.securityConfig().setPolicy("fail");
        }

        configureCommon(camelContext, profile, config, autoConfigured);
    }

    /**
     * Configures camel in general (standalone, quarkus, spring-boot etc) to run in given profile
     *
     * @param camelContext   the camel context
     * @param profile        the profile
     * @param config         the core configuration
     * @param autoConfigured properties that were explicitly configured by the user (may be null)
     */
    public static void configureCommon(
            CamelContext camelContext, String profile, DefaultConfigurationProperties<?> config, Properties autoConfigured) {
        camelContext.getCamelContextExtension().setProfile(profile);

        if (profile == null || profile.isBlank()) {
            // no profile is active
            return;
        }

        if ("dev".equals(profile)) {
            // enable developer features as defaults — user properties can override any of these
            setIfNotConfigured(autoConfigured, "camel.main.devConsoleEnabled", () -> config.setDevConsoleEnabled(true));
            setIfNotConfigured(autoConfigured, "camel.main.camelEventsTimestampEnabled",
                    () -> config.setCamelEventsTimestampEnabled(true));
            setIfNotConfigured(autoConfigured, "camel.main.loadHealthChecks", () -> config.setLoadHealthChecks(true));
            setIfNotConfigured(autoConfigured, "camel.main.sourceLocationEnabled",
                    () -> config.setSourceLocationEnabled(true));
            setIfNotConfigured(autoConfigured, "camel.main.modeline", () -> config.setModeline(true));
            setIfNotConfigured(autoConfigured, "camel.main.loadStatisticsEnabled",
                    () -> config.setLoadStatisticsEnabled(true));
            setIfNotConfigured(autoConfigured, "camel.main.messageHistory", () -> config.setMessageHistory(true));
            setIfNotConfigured(autoConfigured, "camel.main.inflightRepositoryBrowseEnabled",
                    () -> config.setInflightRepositoryBrowseEnabled(true));
            setIfNotConfigured(autoConfigured, "camel.main.messageSizeEnabled", () -> config.setMessageSizeEnabled(true));
            setIfNotConfigured(autoConfigured, "camel.main.endpointRuntimeStatisticsEnabled",
                    () -> config.setEndpointRuntimeStatisticsEnabled(true));
            setIfNotConfigured(autoConfigured, "camel.main.jmxManagementStatisticsLevel",
                    () -> config.setJmxManagementStatisticsLevel(ManagementStatisticsLevel.Extended));
            setIfNotConfigured(autoConfigured, "camel.main.jmxUpdateRouteEnabled",
                    () -> config.setJmxUpdateRouteEnabled(true));
            setIfNotConfigured(autoConfigured, "camel.main.shutdownLogInflightExchangesOnTimeout",
                    () -> config.setShutdownLogInflightExchangesOnTimeout(false));
            setIfNotConfigured(autoConfigured, "camel.main.shutdownTimeout", () -> config.setShutdownTimeout(10));
            setIfNotConfigured(autoConfigured, "camel.main.startupRecorder", () -> config.setStartupRecorder("backlog"));
        }

        if ("prod".equals(profile)) {
            profile = "production"; // use nicer name
        }

        // no special configuration for other kind of profiles

        LOG.info("The application is starting with profile: {}", profile);
    }

    private static void setIfNotConfigured(Properties autoConfigured, String key, Runnable setter) {
        if (autoConfigured == null || !autoConfigured.containsKey(key)) {
            setter.run();
        }
    }

}
