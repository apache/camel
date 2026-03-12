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
import java.time.ZonedDateTime;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.std.StdDeserializer;

import static org.apache.camel.component.salesforce.api.utils.DateTimeHandling.ISO_OFFSET_DATE_TIME;

final class InstantDeserializer extends StdDeserializer<Instant> {

    static final ValueDeserializer<Instant> INSTANCE = new InstantDeserializer();

    private static final long serialVersionUID = 1L;

    private InstantDeserializer() {
        super(Instant.class);
    }

    @Override
    public Instant deserialize(final JsonParser parser, final DeserializationContext context) {
        try {
            final String text = parser.getText();
            final ZonedDateTime zonedDateTime = ZonedDateTime.parse(text, ISO_OFFSET_DATE_TIME);
            return zonedDateTime.toInstant();
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize Instant", e);
        }
    }

}
