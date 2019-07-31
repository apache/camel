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
package org.apache.camel.util;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

public final class StreamUtils {
    private StreamUtils() {
    }

    /**
     * Creates a stream on the given collection if it is not null
     *
     * @param value  the collection
     * @return A stream of elements or an empty stream if the collection is null
     */
    public static <C> Stream<C> stream(Collection<C> value) {
        return value != null ? value.stream() : Stream.empty();
    }

    /**
     * Creates a stream of entries on the given Map if it is not null
     *
     * @param value  the map
     * @return A stream of entries or an empty stream if the collection is null
     */
    public static <K, V> Stream<Map.Entry<K, V>> stream(Map<K, V> value) {
        return value != null ? value.entrySet().stream() : Stream.empty();
    }
}
