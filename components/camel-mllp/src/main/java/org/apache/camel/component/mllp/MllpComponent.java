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

package org.apache.camel.component.mllp;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link MllpEndpoint}.
 */
public class MllpComponent extends UriEndpointComponent {
    public static final String MLLP_LOG_PHI_PROPERTY = "org.apache.camel.component.mllp.logPHI";
    public static final String MLLP_LOG_PHI_MAX_BYTES_PROPERTY = "org.apache.camel.component.mllp.logPHI.maxBytes";
    public static final boolean DEFAULT_LOG_PHI = true;
    public static final int DEFAULT_LOG_PHI_MAX_BYTES = 5120;

    static Logger log = LoggerFactory.getLogger(MllpComponent.class);
    static Boolean logPhi;
    static Integer logPhiMaxBytes;

    MllpConfiguration configuration;

    public MllpComponent() {
        super(MllpEndpoint.class);
    }

    public MllpComponent(CamelContext context) {
        super(context, MllpEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uriString, String remaining, Map<String, Object> parameters) throws Exception {
        MllpEndpoint endpoint = new MllpEndpoint(uriString, this, hasConfiguration() ? configuration.copy() : new MllpConfiguration());

        endpoint.setBridgeErrorHandler(true);

        setProperties(endpoint, parameters);

        // Make sure it has a host - may just be a port
        int colonIndex = remaining.indexOf(':');
        if (-1 != colonIndex) {
            endpoint.setHostname(remaining.substring(0, colonIndex));
            endpoint.setPort(Integer.parseInt(remaining.substring(colonIndex + 1)));
        } else {
            // No host specified - leave the default host and set the port
            endpoint.setPort(Integer.parseInt(remaining));
        }

        return endpoint;
    }

    public static boolean hasLogPhi() {
        return logPhi != null;
    }

    public static boolean isLogPhi() {
        if (hasLogPhi()) {
            return logPhi;
        }

        boolean answer = DEFAULT_LOG_PHI;
        String logPhiProperty = System.getProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY);

        if (logPhiProperty != null) {
            answer = Boolean.valueOf(logPhiProperty);
        }

        return answer;
    }

    /**
     * Set the component to log PHI data.
     *
     * @param logPhi true enables PHI logging; false disables it.
     */
    public static void setLogPhi(Boolean logPhi) {
        MllpComponent.logPhi = logPhi;
    }

    public static boolean hasLogPhiMaxBytes() {
        return logPhiMaxBytes != null;
    }

    public static int getLogPhiMaxBytes() {
        if (hasLogPhiMaxBytes()) {
            return logPhiMaxBytes;
        }

        int answer = DEFAULT_LOG_PHI_MAX_BYTES;
        String logPhiProperty = System.getProperty(MllpComponent.MLLP_LOG_PHI_MAX_BYTES_PROPERTY);

        if (logPhiProperty != null && !logPhiProperty.isEmpty()) {
            try {
                answer = Integer.valueOf(logPhiProperty);
            } catch (NumberFormatException numberFormatException) {
                log.warn("Invalid Interger value '{}' for system property {} - using default value of {}", logPhiProperty, MllpComponent.MLLP_LOG_PHI_MAX_BYTES_PROPERTY, answer);
                // use DEFAULT_LOG_PHI_MAX_BYTES for a invalid entry
            }
        }

        return answer;
    }

    /**
     * Set the maximum number of bytes of PHI that will be logged in a log entry.
     *
     * @param logPhiMaxBytes the maximum number of bytes to log.
     */
    public static void setLogPhiMaxBytes(Integer logPhiMaxBytes) {
        MllpComponent.logPhiMaxBytes = logPhiMaxBytes;
    }



    public boolean hasConfiguration() {
        return configuration != null;
    }

    public MllpConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the default configuration to use when creating MLLP endpoints.
     *
     * @param configuration the default configuration.
     */
    public void setConfiguration(MllpConfiguration configuration) {
        this.configuration = configuration;
    }
}
