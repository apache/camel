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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;

import static org.apache.camel.component.salesforce.api.utils.DateTimeHandling.ISO_OFFSET_DATE_TIME;

public class TimeModule extends SimpleModule {

    private static final LocalDateDeserializer LOCAL_DATE_DESERIALIZER = new LocalDateDeserializer(DateTimeFormatter.ISO_DATE);

    private static final LocalDateSerializer LOCAL_DATE_SERIALIZER = new LocalDateSerializer(DateTimeFormatter.ISO_DATE);

    private static final long serialVersionUID = 1L;

    private static final ZonedDateTimeSerializer ZONED_DATE_TIME_SERIALIZER = new ZonedDateTimeSerializer(ISO_OFFSET_DATE_TIME);

    private final JavaTimeModule delegate = new JavaTimeModule();

    public TimeModule() {
        addSerializer(LocalDate.class, LOCAL_DATE_SERIALIZER);
        addDeserializer(LocalDate.class, LOCAL_DATE_DESERIALIZER);

        addSerializer(LocalDateTime.class, LocalDateTimeSerializer.INSTANCE);
        addDeserializer(LocalDateTime.class, LocalDateTimeDeserializer.INSTANCE);

        addSerializer(OffsetDateTime.class, OffsetDateTimeSerializer.INSTANCE);
        addDeserializer(OffsetDateTime.class, OffsetDateTimeDeserializer.INSTANCE);

        addSerializer(ZonedDateTime.class, ZONED_DATE_TIME_SERIALIZER);
        addDeserializer(ZonedDateTime.class, ZonedDateTimeDeserializer.INSTANCE);

        addSerializer(Instant.class, InstantSerializer.INSTANCE);
        addDeserializer(Instant.class, InstantDeserializer.INSTANCE);

        addSerializer(OffsetTime.class, OffsetTimeSerializer.INSTANCE);
        addDeserializer(OffsetTime.class, OffsetTimeDeserializer.INSTANCE);
    }

    @Override
    public void setupModule(final SetupContext context) {
        delegate.setupModule(context);
        super.setupModule(context);
    }

}
