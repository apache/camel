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
package org.apache.camel.component.platform.http.spi;

import java.util.Optional;

import org.apache.camel.CamelContextAware;
import org.apache.camel.StaticService;

/**
 * Factory to abstract the creation of the Plugin Registry for camel-platform-http.
 */
public interface PlatformHttpPluginRegistry extends CamelContextAware, StaticService {

    String FACTORY = "platform-http/plugin-registry";

    /**
     * Resolve a plugin by id
     *
     * @param  id   the plugin id
     * @param  type the plugin class type
     * @return      the plugin if found
     */
    <T extends PlatformHttpPlugin> Optional<T> resolvePluginById(String id, Class<T> type);

    /**
     * Register the plugin into the registry.
     *
     * @param  plugin the plugin
     * @return        true if the plugin was added, or false if already exists
     */
    boolean register(PlatformHttpPlugin plugin);
}
