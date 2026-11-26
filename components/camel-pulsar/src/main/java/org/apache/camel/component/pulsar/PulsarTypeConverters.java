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
package org.apache.camel.component.pulsar;

import java.util.Locale;

import org.apache.camel.Converter;
import org.apache.pulsar.client.api.BatcherBuilder;

/**
 * Type converters for the Pulsar options whose values are not Java enums.
 */
@Converter(generateLoader = true)
public final class PulsarTypeConverters {

    private PulsarTypeConverters() {
    }

    /**
     * Converts DEFAULT or KEY_BASED (any case) to the batcher builder of the same name in {@link BatcherBuilder}.
     */
    @Converter
    public static BatcherBuilder toBatcherBuilder(String value) {
        String name = value.trim().toUpperCase(Locale.ROOT);
        return switch (name) {
            case "DEFAULT" -> BatcherBuilder.DEFAULT;
            case "KEY_BASED" -> BatcherBuilder.KEY_BASED;
            default -> throw new IllegalArgumentException(
                    "Unknown batcherBuilder: " + value + ". Use DEFAULT, KEY_BASED or a reference to a BatcherBuilder bean.");
        };
    }
}
