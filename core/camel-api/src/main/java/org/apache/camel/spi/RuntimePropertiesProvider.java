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
package org.apache.camel.spi;

import java.util.Map;

/**
 * SPI that allows runtimes (Spring Boot, Quarkus, etc.) to contribute application properties for display purposes, such
 * as the Properties dev console.
 * <p>
 * Camel's {@link PropertiesComponent#loadProperties()} only returns properties from Camel's own sources (initial
 * properties, .properties files, override properties). Application properties managed by external configuration systems
 * (e.g. Spring {@code Environment}, SmallRye {@code Config}) are not included.
 * <p>
 * This SPI bridges that gap: runtimes register an implementation that enumerates their properties, and the dev console
 * merges them with the Camel-managed properties. The properties returned by this SPI are read-only and are NOT used for
 * placeholder resolution — they are purely for display and introspection.
 *
 * @since 4.22
 */
public interface RuntimePropertiesProvider {

    /**
     * Returns the name of the runtime or source providing these properties (e.g. "Spring Boot", "Quarkus").
     */
    String getSource();

    /**
     * Enumerates application properties from the runtime's configuration system.
     *
     * @return a map of property key to value (never null)
     */
    Map<String, Object> getProperties();
}
