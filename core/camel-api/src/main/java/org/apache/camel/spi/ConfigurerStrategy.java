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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for managing the lifecycle of bootstrap-phase {@link PropertyConfigurer} instances generated for
 * {@link Configurer @Configurer(bootstrap = true)} types.
 * <p/>
 * During {@link org.apache.camel.CamelContext} startup, generated configurers for bootstrap-only types (such as main
 * configuration classes that are only configured once) are held in memory. After the context has finished starting,
 * calling {@link #clearBootstrapConfigurers()} releases those maps to reduce the steady-state memory footprint. Modules
 * register their own cleaner via {@link #addBootstrapConfigurerClearer(Runnable)} during initialization so that the
 * strategy does not need to know their internal data structures.
 *
 * @see   Configurer
 * @see   PropertyConfigurer
 * @since 3.7
 */
public abstract class ConfigurerStrategy {

    private static final List<Runnable> BOOTSTRAP_CLEARERS = new ArrayList<>();

    public static void addBootstrapConfigurerClearer(Runnable strategy) {
        Objects.requireNonNull(strategy, "strategy");
        BOOTSTRAP_CLEARERS.add(strategy);
    }

    /**
     * Clears the bootstrap configurers map. Clearing this map allows Camel to reduce memory footprint.
     */
    public static void clearBootstrapConfigurers() {
        for (Runnable run : BOOTSTRAP_CLEARERS) {
            run.run();
        }
        BOOTSTRAP_CLEARERS.clear();
    }

}
