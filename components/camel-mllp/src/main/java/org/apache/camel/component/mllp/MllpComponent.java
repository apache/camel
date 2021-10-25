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
package org.apache.camel.component.mllp;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;

@Component("mllp")
public class MllpComponent extends DefaultComponent {

    @Metadata(label = "advanced", defaultValue = "true")
    private boolean logPhi = true;
    @Metadata(label = "advanced", defaultValue = "5120")
    private int logPhiMaxBytes = 5120;
    @Metadata(label = "advanced", defaultValue = "ISO-8859-1")
    private String defaultCharset = "ISO_8859_1";
    @Metadata
    private MllpConfiguration configuration;

    public MllpComponent() {
        // bridge error handler by default
        setBridgeErrorHandler(true);
    }

    public MllpComponent(CamelContext context) {
        super(context);
        // bridge error handler by default
        setBridgeErrorHandler(true);
    }

    @Override
    protected Endpoint createEndpoint(String uriString, String remaining, Map<String, Object> parameters) throws Exception {
        MllpEndpoint endpoint
                = new MllpEndpoint(uriString, this, hasConfiguration() ? configuration.copy() : new MllpConfiguration());

        endpoint.setCharsetName(getDefaultCharset());

        // Make sure it has a host - may just be a port
        int colonIndex = remaining.indexOf(':');
        if (colonIndex != -1) {
            endpoint.setHostname(remaining.substring(0, colonIndex));
            endpoint.setPort(CamelContextHelper.parseInt(getCamelContext(), remaining.substring(colonIndex + 1)));
        } else {
            // No host specified - leave the default host and set the port
            endpoint.setPort(CamelContextHelper.parseInt(getCamelContext(), remaining));
        }

        setProperties(endpoint, parameters);
        return endpoint;
    }

    public Boolean getLogPhi() {
        return logPhi;
    }

    /**
     * Whether to log PHI
     */
    public void setLogPhi(Boolean logPhi) {
        this.logPhi = logPhi;
    }

    public int getLogPhiMaxBytes() {
        return logPhiMaxBytes;
    }

    /**
     * Set the maximum number of bytes of PHI that will be logged in a log entry.
     */
    public void setLogPhiMaxBytes(Integer logPhiMaxBytes) {
        this.logPhiMaxBytes = logPhiMaxBytes;
    }

    public String getDefaultCharset() {
        return defaultCharset;
    }

    /**
     * Set the default character set to use for byte[] to/from String conversions.
     */
    public void setDefaultCharset(String name) {
        this.defaultCharset = name;
    }

    public boolean hasConfiguration() {
        return configuration != null;
    }

    public MllpConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the default configuration to use when creating MLLP endpoints.
     */
    public void setConfiguration(MllpConfiguration configuration) {
        this.configuration = configuration;
    }
}
