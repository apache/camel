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
import org.apache.camel.spi.Metadata;

/**
 * Global configuration for GenAI observability in Camel AI components.
 */
@Configurer(extended = true)
public class AiObservabilityConfigurationProperties implements BootstrapCloseable {

    private AiConfigurationProperties parent;

    @Metadata(defaultValue = "true")
    private boolean enabled = true;

    public AiObservabilityConfigurationProperties(AiConfigurationProperties parent) {
        this.parent = parent;
    }

    public AiConfigurationProperties end() {
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
     * Enables GenAI observability for Camel AI producers (OpenTelemetry spans and Micrometer metrics when backends are
     * present).
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Enables GenAI observability for Camel AI producers (OpenTelemetry spans and Micrometer metrics when backends are
     * present).
     */
    public AiObservabilityConfigurationProperties withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }
}
