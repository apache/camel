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
 * Global configuration for Camel AI features.
 */
@Configurer(extended = true)
public class AiConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;
    private AiObservabilityConfigurationProperties observabilityConfigurationProperties;

    public AiConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
        if (observabilityConfigurationProperties != null) {
            observabilityConfigurationProperties.close();
            observabilityConfigurationProperties = null;
        }
    }

    /**
     * To configure GenAI observability.
     */
    public AiObservabilityConfigurationProperties observability() {
        if (observabilityConfigurationProperties == null) {
            observabilityConfigurationProperties = new AiObservabilityConfigurationProperties(this);
        }
        return observabilityConfigurationProperties;
    }

    /**
     * Whether there has been any GenAI observability configuration specified.
     */
    public boolean hasObservabilityConfiguration() {
        return observabilityConfigurationProperties != null;
    }
}
