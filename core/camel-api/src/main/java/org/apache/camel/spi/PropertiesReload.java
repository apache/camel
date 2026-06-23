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

import java.util.Properties;

/**
 * Listener notified when a {@link java.util.Properties} source is re-loaded at runtime, for example after a properties
 * file changes.
 * <p/>
 * When live reloading is enabled, the {@link PropertiesComponent} invokes {@link #onReload(String, Properties)} with
 * the resource name and the freshly loaded properties, allowing Camel to react to configuration changes without a
 * restart.
 * <p/>
 * See <a href="https://camel.apache.org/manual/using-propertyplaceholder.html">Using PropertyPlaceholder</a> in the
 * Camel user manual.
 *
 * @see   PropertiesComponent
 * @since 4.0
 */
@FunctionalInterface
public interface PropertiesReload {

    /**
     * Callback when the properties is re-loaded.
     *
     * @param  name       name of the resource such as the file name (absolute)
     * @param  properties the properties that was re-loaded
     * @throws Exception  error reloading properties
     */
    void onReload(String name, Properties properties) throws Exception;

}
