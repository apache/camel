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

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import static org.apache.camel.component.salesforce.api.utils.DateTimeHandling.ISO_OFFSET_DATE_TIME;

final class OffsetDateTimeDeserializer extends ValueDeserializer<OffsetDateTime> {

    static final ValueDeserializer<OffsetDateTime> INSTANCE = new OffsetDateTimeDeserializer();

    private OffsetDateTimeDeserializer() {
    }

    @Override
    public OffsetDateTime deserialize(final JsonParser p, final DeserializationContext ctxt) {
        try {
            final String text = p.getText();
            final ZonedDateTime zonedDateTime = ZonedDateTime.parse(text, ISO_OFFSET_DATE_TIME);
            return zonedDateTime.toOffsetDateTime();
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize OffsetDateTime", e);
        }
    }

}
