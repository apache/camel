/**
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
package org.apache.camel.util.function;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Bindings {
    private Bindings() {
    }

    public static <T1, T2> Consumer<T2> bind(T1 v1, BiConsumer<T1, T2> consumer) {
        return v2 -> consumer.accept(v1, v2);
    }

    public static <T1, T2, R> Function<T2, R> bind(T1 v1, BiFunction<T1, T2, R> function) {
        return v2 -> function.apply(v1, v2);
    }
}
