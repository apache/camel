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
package org.apache.camel.component.salesforce.api.utils;

import java.time.OffsetTime;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.std.StdSerializer;

import static org.apache.camel.component.salesforce.api.utils.DateTimeHandling.ISO_OFFSET_TIME;

final class OffsetTimeSerializer extends StdSerializer<OffsetTime> {

    static final ValueSerializer<OffsetTime> INSTANCE = new OffsetTimeSerializer();

    private static final long serialVersionUID = 1L;

    private OffsetTimeSerializer() {
        super(OffsetTime.class);
    }

    @Override
    public void serialize(final OffsetTime value, final JsonGenerator gen, final SerializationContext serializers) {
        try {
            final String formatted = ISO_OFFSET_TIME.format(value);
            gen.writeString(formatted);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize OffsetTime", e);
        }
    }

}
