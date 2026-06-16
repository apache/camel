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

import org.apache.camel.Ordered;
import org.snakeyaml.engine.v2.api.ConstructNode;

public interface YamlDeserializerResolver extends Ordered {

    /**
     * Classpath resource containing YAML deserializer resolver class names to load automatically.
     */
    String RESOURCE_PATH = "META-INF/services/org/apache/camel/YamlDeserializerResolver";

    int ORDER_DEFAULT = 0;
    int ORDER_HIGHEST = Ordered.HIGHEST;
    int ORDER_LOWEST = Ordered.LOWEST;

    /**
     * Resolves a YAML step name or model class name to a SnakeYAML constructor.
     * <p/>
     * Return {@code null} when this resolver does not own the given id. Resolver implementations should not throw for
     * unsupported ids. If multiple resolvers return a constructor for the same id, the first resolver in precedence
     * order wins. The selected constructor may be cached by the YAML deserialization context for the lifetime of that
     * context.
     *
     * @param  id YAML node name, route step name, or Java model class name
     * @return    constructor for supported ids, or {@code null} for unsupported ids
     */
    ConstructNode resolve(String id);

    default ConstructNode resolve(Class<?> type) {
        return resolve(type.getName());
    }

    /**
     * Gets the resolver precedence.
     * <p/>
     * Lower values have higher precedence. Component-provided resolvers should normally use an order at or above
     * {@link #ORDER_DEFAULT}. Use values below {@link #ORDER_DEFAULT} only when intentionally overriding a built-in
     * YAML DSL resolver.
     */
    @Override
    default int getOrder() {
        return ORDER_DEFAULT;
    }
}
