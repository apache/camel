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
 * SPI for loading {@link RoutesBuilder} from a {@link Resource}.
 */
public interface RoutesBuilderLoader extends StaticService, CamelContextAware {

    /**
     * Service factory base path for language specific loaders.
     */
    String FACTORY_PATH = "META-INF/services/org/apache/camel/routes-loader/";

    /**
     * Service factory group.
     */
    String FACTORY_GROUP = "routes-loader";

    /**
     * The supported file extension.
     * <p/>
     * Implementations should support a single extension only.
     */
    String getSupportedExtension();

    /**
     * Loads {@link RoutesBuilder} from {@link Resource}.
     *
     * @param  resource the resource to be loaded.
     * @return          a {@link RoutesBuilder}
     */
    RoutesBuilder loadRoutesBuilder(Resource resource) throws Exception;
}
