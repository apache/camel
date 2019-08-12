/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class DefinitionPropertiesProviderHelper {

    private static final Map<Class, Function<Object, PropertyPlaceholderAware>> MAP;
    static {
        Map<Class, Function<Object, PropertyPlaceholderAware>> map = new HashMap<>();
        map.put(FromDefinition.class, FromDefinitionPropertiesProvider::new);
        map.put(LogDefinition.class, LogDefinitionPropertiesProvider::new);
        map.put(ToDefinition.class, ToDefinitionPropertiesProvider::new);
        MAP = map;
    }

    public static Optional<PropertyPlaceholderAware> provider(Object definition) {
        Function<Object, PropertyPlaceholderAware> func = MAP.get(definition.getClass());
        if (func != null) {
            return Optional.of(func.apply(definition));
        }
        return Optional.empty();
    }

}
