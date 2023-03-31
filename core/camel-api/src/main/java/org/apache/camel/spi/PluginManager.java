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

import java.util.function.Supplier;

/**
 * A manager for internal plugins. This is part of the internal Camel API and not meant for public usage.
 */

public interface PluginManager {
    /**
     * Gets a plugin of the given type.
     *
     * @param  type the type of the extension
     * @return      the extension, or <tt>null</tt> if no extension has been installed.
     */
    <T> T getContextPlugin(Class<T> type);

    /**
     * Allows installation of custom plugins to the Camel context.
     *
     * @param type   the type of the extension
     * @param module the instance of the extension
     */
    <T> void addContextPlugin(Class<T> type, T module);

    /**
     * Allows lazy installation of custom plugins to the Camel context.
     *
     * @param type   the type of the extension
     * @param module the instance of the extension
     */
    <T> void lazyAddContextPlugin(Class<T> type, Supplier<T> module);
}
