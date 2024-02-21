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
package org.apache.camel.component.http;

import org.apache.camel.Converter;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Some converter methods to build different types used to configure the component.
 */
@Converter(generateLoader = true)
public final class HttpConverters {

    private HttpConverters() {
        // Helper class
    }

    @Converter
    public static Timeout toTimeout(long millis) {
        return Timeout.ofMilliseconds(millis);
    }

    @Converter
    public static Timeout toTimeout(String millis) {
        return Timeout.ofMilliseconds(Long.parseLong(millis));
    }

    @Converter
    public static TimeValue toTimeValue(long millis) {
        return TimeValue.ofMilliseconds(millis);
    }

    @Converter
    public static TimeValue toTimeValue(String millis) {
        return TimeValue.ofMilliseconds(Long.parseLong(millis));
    }
}
