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

import org.apache.camel.CamelContextAware;
import org.apache.camel.StatefulService;

/**
 * Service Provider Interface (SPI) for discovering and loading {@link ContextServicePlugin} implementations during
 * CamelContext initialization.
 * <p>
 * This resolver is responsible for automatically discovering and loading plugins that extend Camel's functionality
 * through the Java ServiceLoader mechanism. Implementations of this interface participate in the Camel service
 * lifecycle and are typically invoked during CamelContext startup to initialize third-party components and extensions.
 * <p>
 * The plugin resolution process typically occurs after the CamelContext has been created and configured but before
 * routes are started, ensuring that all plugins have the opportunity to modify or extend the context as needed.
 * <p>
 * Implementations must be stateful services that can be started and stopped as part of the normal Camel lifecycle, and
 * they must be aware of the CamelContext they are operating on.
 *
 * @see ContextServicePlugin
 * @see CamelContextAware
 * @see StatefulService
 */
public interface ContextServiceLoaderPluginResolver extends CamelContextAware, StatefulService {}
