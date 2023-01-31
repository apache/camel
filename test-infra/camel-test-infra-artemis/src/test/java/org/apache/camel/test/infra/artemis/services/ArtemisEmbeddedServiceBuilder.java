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
package org.apache.camel.test.infra.artemis.services;

import java.util.function.Consumer;

import org.apache.activemq.artemis.core.config.Configuration;

public class ArtemisEmbeddedServiceBuilder {

    private boolean isPersistent;
    private Consumer<Configuration> artemisConfiguration;

    public ArtemisEmbeddedServiceBuilder() {
    }

    public ArtemisEmbeddedServiceBuilder withPersistent(boolean persistent) {
        isPersistent = persistent;

        return this;
    }

    public ArtemisEmbeddedServiceBuilder withCustomConfiguration(Consumer<Configuration> configuration) {
        artemisConfiguration = configuration;

        return this;
    }

    public ArtemisService build() {
        AbstractArtemisEmbeddedService artemisService;
        if (isPersistent) {
            artemisService = new ArtemisPersistentVMService();
        } else {
            artemisService = new ArtemisVMService();
        }

        if (artemisService != null) {
            artemisService.customConfiguration(artemisConfiguration);
        }

        return artemisService;
    }
}
