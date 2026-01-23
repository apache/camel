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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.Date;

import tools.jackson.databind.module.SimpleModule;

public class TimeModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    public TimeModule() {
        // Register custom serializers/deserializers for Salesforce-specific time formats
        addSerializer(Date.class, DateSerializer.INSTANCE);

        addSerializer(LocalDateTime.class, LocalDateTimeSerializer.INSTANCE);
        addDeserializer(LocalDateTime.class, LocalDateTimeDeserializer.INSTANCE);

        addSerializer(OffsetDateTime.class, OffsetDateTimeSerializer.INSTANCE);
        addDeserializer(OffsetDateTime.class, OffsetDateTimeDeserializer.INSTANCE);

        addSerializer(ZonedDateTime.class, ZonedDateTimeSerializer.INSTANCE);
        addDeserializer(ZonedDateTime.class, ZonedDateTimeDeserializer.INSTANCE);

        addSerializer(Instant.class, InstantSerializer.INSTANCE);
        addDeserializer(Instant.class, InstantDeserializer.INSTANCE);

        addSerializer(OffsetTime.class, OffsetTimeSerializer.INSTANCE);
        addDeserializer(OffsetTime.class, OffsetTimeDeserializer.INSTANCE);
    }

}
