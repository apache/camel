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
package org.apache.camel.dsl.yaml.common;

import java.util.Map;

import org.apache.camel.CamelContext;

/**
 * Strategy for discovering automatically available {@link YamlDeserializerResolver} instances.
 * <p/>
 * The default implementation discovers resolver class names from {@link YamlDeserializerResolver#RESOURCE_PATH}.
 * Runtimes that perform build-time discovery can register their own provider as a Camel context plugin and return the
 * resolvers without scanning classpath resources at runtime.
 */
public interface YamlDeserializerResolverProvider {

    /**
     * Finds automatically available YAML deserializer resolvers.
     * <p/>
     * The {@code camelContext} argument is never {@code null}. Implementations must return a non-{@code null} map. Use
     * {@code Collections.emptyMap()} when no resolvers are available. Map keys must be stable, non-{@code null} names
     * used for diagnostics and deterministic ordering. Map values must be non-{@code null}
     * {@link YamlDeserializerResolver} instances. Discovery failures should be reported by throwing a runtime
     * exception.
     *
     * @param  camelContext the Camel context used for resolver discovery and instantiation
     * @return              discovered resolvers keyed by a stable name used for diagnostics and deterministic ordering
     */
    Map<String, YamlDeserializerResolver> findResolvers(CamelContext camelContext);
}
