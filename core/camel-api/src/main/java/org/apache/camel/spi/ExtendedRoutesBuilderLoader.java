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

import java.util.Collection;

import org.apache.camel.RoutesBuilder;

/**
 * An extended {@link RoutesBuilderLoader} that is capable of loading from multiple resources in one unit (such as
 * compiling them together).
 */
public interface ExtendedRoutesBuilderLoader extends RoutesBuilderLoader {

    /**
     * Pre-parses the {@link RoutesBuilder} from multiple {@link Resource}s.
     *
     * This is used during bootstrap, to eager detect configurations from route DSL resources which makes it possible to
     * specify configurations that affect the bootstrap, such as by camel-jbang and camel-yaml-dsl.
     *
     * @param resources the resources to be pre parsed.
     */
    default void preParseRoutes(Collection<Resource> resources) throws Exception {
        // by default parse one-by-one
        for (Resource resource : resources) {
            preParseRoute(resource);
        }
    }

    /**
     * Loads {@link RoutesBuilder} from multiple {@link Resource}s.
     *
     * @param  resources the resources to be loaded.
     * @return           a set of loaded {@link RoutesBuilder}s
     */
    Collection<RoutesBuilder> loadRoutesBuilders(Collection<Resource> resources) throws Exception;

}
