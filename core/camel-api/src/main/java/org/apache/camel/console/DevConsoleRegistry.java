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
package org.apache.camel.console;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.StaticService;
import org.apache.camel.spi.IdAware;
import org.apache.camel.util.ObjectHelper;

/**
 * A registry for dev console.
 */
public interface DevConsoleRegistry extends CamelContextAware, StaticService, IdAware {

    /**
     * Service factory name.
     */
    String NAME = "default-registry";

    /**
     * Service factory key.
     */
    String FACTORY = "dev-console/" + NAME;

    /**
     * Whether dev console is enabled globally
     */
    boolean isEnabled();

    /**
     * Whether dev console is enabled globally
     */
    void setEnabled(boolean enabled);

    /**
     * Resolves {@link DevConsole} by id.
     *
     * Will first lookup in this {@link DevConsoleRegistry} and then {@link org.apache.camel.spi.Registry}, and lastly
     * do classpath scanning via {@link org.apache.camel.spi.annotations.ServiceFactory}.
     *
     * @return either {@link DevConsole}, or <tt>null</tt> if none found.
     */
    DevConsole resolveById(String id);

    /**
     * Registers a {@link DevConsole}.
     */
    boolean register(DevConsole console);

    /**
     * Unregisters a {@link DevConsole}.
     */
    boolean unregister(DevConsole console);

    /**
     * A collection of dev console IDs.
     */
    default Collection<String> getConsoleIDs() {
        return stream()
                .map(DevConsole::getId)
                .toList();
    }

    /**
     * Returns the dev console identified by the given <code>id</code> if available.
     */
    default Optional<DevConsole> getConsole(String id) {
        return stream()
                .filter(r -> ObjectHelper.equal(r.getId(), id))
                .findFirst();
    }

    /**
     * Returns an optional {@link DevConsoleRegistry}, by default no registry is present, and it must be explicit
     * activated. Components can register/unregister dev consoles in response to life-cycle events (i.e. start/stop).
     *
     * This registry is not used by the camel context, but it is up to the implementation to properly use it.
     */
    static DevConsoleRegistry get(CamelContext context) {
        return context.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class);
    }

    /**
     * Returns a sequential {@code Stream} with the known {@link DevConsole} as its source.
     */
    Stream<DevConsole> stream();

    /**
     * Loads custom dev consoles by scanning classpath.
     */
    void loadDevConsoles();

    /**
     * Loads custom dev consoles by scanning classpath.
     *
     * @param force force re-scanning such as when additional JARs has been added to the classpath that can include
     *              custom dev consoles
     */
    void loadDevConsoles(boolean force);

}
