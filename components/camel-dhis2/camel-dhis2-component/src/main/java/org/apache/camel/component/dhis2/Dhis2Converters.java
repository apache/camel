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
package org.apache.camel.component.dhis2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.dhis2.api.Dhis2Resource;
import org.apache.camel.spi.TypeConverterRegistry;

@Converter(generateLoader = true)
public final class Dhis2Converters {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Dhis2Converters() {

    }

    @Converter(fallback = true)
    public static <T> T convertTo(Class<T> type, Exchange exchange, Object resource, TypeConverterRegistry registry) {
        if (resource instanceof Dhis2Resource && type.getName().startsWith("org.hisp.dhis.api.model")) {
            return OBJECT_MAPPER.convertValue(resource, type);
        } else {
            return null;
        }
    }
}
