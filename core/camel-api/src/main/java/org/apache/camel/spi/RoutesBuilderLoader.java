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
import org.apache.camel.RoutesBuilder;
import org.apache.camel.StaticService;

/**
 * SPI that loads {@link org.apache.camel.RoutesBuilder} instances from a {@link Resource} identified by file extension.
 * <p/>
 * Implementations are keyed by the extension they support (e.g. {@code java}, {@code xml}, {@code yaml}, {@code kts})
 * and are discovered at path {@link #FACTORY_PATH}. At startup, {@link org.apache.camel.CamelContext} iterates over the
 * configured route resource locations, selects the appropriate loader via {@link #isSupportedExtension(String)}, and
 * calls {@link #loadRoutesBuilder(Resource)} to obtain one or more {@link org.apache.camel.RoutesBuilder}s that are
 * then added to the context. Before routes are loaded, {@link #preParseRoute(Resource)} may be called to extract
 * {@code camel.main.*} bootstrap configuration from the DSL source without fully loading the routes; this allows the
 * context to apply route-shaping settings (e.g. component overrides) before the full load.
 *
 * @see   Resource
 * @since 3.8
 */
public interface RoutesBuilderLoader extends StaticService, CamelContextAware {

    /**
     * Service factory base path for language specific loaders.
     */
    String FACTORY_PATH = "META-INF/services/org/apache/camel/routes-loader/";

    /**
     * The supported file extension.
     * <p/>
     * Implementations should support a single extension only.
     */
    String getSupportedExtension();

    /**
     * Whether the file extension is supported
     */
    default boolean isSupportedExtension(String extension) {
        return getSupportedExtension().equals(extension);
    }

    /**
     * Loads {@link RoutesBuilder} from {@link Resource}.
     *
     * @param  resource the resource to be loaded.
     * @return          a {@link RoutesBuilder}
     */
    RoutesBuilder loadRoutesBuilder(Resource resource) throws Exception;

    /**
     * Pre-parses the {@link RoutesBuilder} from {@link Resource}.
     *
     * This is used during bootstrap, to eager detect configurations from route DSL resources which makes it possible to
     * specify configurations that affect the bootstrap, such as by camel-jbang and camel-yaml-dsl.
     *
     * @param resource the resource to be pre parsed.
     */
    default void preParseRoute(Resource resource) throws Exception {
        // noop
    }

}
