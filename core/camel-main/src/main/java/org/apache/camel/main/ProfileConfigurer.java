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
import org.apache.camel.ManagementStatisticsLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configure Camel Main with the chosen profile.
 *
 * This is for Camel JBang and Standalone Camel, not Spring Boot or Quarkus; as they have their own profile concept.
 */
public class ProfileConfigurer {

    protected static final Logger LOG = LoggerFactory.getLogger(ProfileConfigurer.class);

    /**
     * Configures camel-main to run in given profile
     *
     * @param camelContext the camel context
     * @param profile      the profile
     * @param config       the main configuration
     */
    public static void configureMain(CamelContext camelContext, String profile, MainConfigurationProperties config) {
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
        }

        configureCommon(camelContext, profile, config);
    }

    /**
     * Configures camel in general (standalone, quarkus, spring-boot etc) to run in given profile
     *
     * @param camelContext the camel context
     * @param profile      the profile
     * @param config       the core configuration
     */
    public static void configureCommon(
            CamelContext camelContext, String profile, DefaultConfigurationProperties<?> config) {
        camelContext.getCamelContextExtension().setProfile(profile);

        if (profile == null || profile.isBlank()) {
            // no profile is active
            return;
        }

        if ("dev".equals(profile)) {
            // always enable developer console as it is needed by camel-cli-connector
            config.setDevConsoleEnabled(true);
            // and enable a bunch of other stuff that gives more details for developers
            config.setCamelEventsTimestampEnabled(true);
            config.setLoadHealthChecks(true);
            config.setSourceLocationEnabled(true);
            config.setModeline(true);
            config.setLoadStatisticsEnabled(true);
            config.setMessageHistory(true);
            config.setInflightRepositoryBrowseEnabled(true);
            config.setEndpointRuntimeStatisticsEnabled(true);
            config.setJmxManagementStatisticsLevel(ManagementStatisticsLevel.Extended);
            config.setJmxUpdateRouteEnabled(true);
            config.setShutdownLogInflightExchangesOnTimeout(false);
            config.setShutdownTimeout(10);
            config.setStartupRecorder("backlog");
        }

        if ("prod".equals(profile)) {
            profile = "production"; // use nicer name
        }

        // no special configuration for other kind of profiles

        LOG.info("The application is starting with profile: {}", profile);
    }
}
