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

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;

/**
 * Global configuration for MDC
 */
@Configurer(extended = true)
public class MdcConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    private boolean enabled;
    private String customExchangeHeaders;
    private String customExchangeProperties;

    public MdcConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * To enable MDC service
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCustomExchangeHeaders() {
        return customExchangeHeaders;
    }

    /**
     * Provide the headers you would like to use in the logging. Use `&#42;` value to include all available headers
     */
    public void setCustomExchangeHeaders(String customExchangeHeaders) {
        this.customExchangeHeaders = customExchangeHeaders;
    }

    public String getCustomExchangeProperties() {
        return customExchangeProperties;
    }

    /**
     * Provide the properties you would like to use in the logging. Use `&#42;` value to include all available
     * properties
     */
    public void setCustomExchangeProperties(String customExchangeProperties) {
        this.customExchangeProperties = customExchangeProperties;
    }

    /**
     * To enable MDC service
     */
    public MdcConfigurationProperties withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Provide the headers you would like to use in the logging. Use `&#42;` value to include all available headers
     */
    public MdcConfigurationProperties withCustomExchangeHeaders(String customExchangeHeaders) {
        this.customExchangeHeaders = customExchangeHeaders;
        return this;
    }

    /**
     * Provide the properties you would like to use in the logging. Use `&#42;` value to include all available
     * properties
     */
    public MdcConfigurationProperties withCustomExchangeProperties(String customExchangeProperties) {
        this.customExchangeProperties = customExchangeProperties;
        return this;
    }
}
