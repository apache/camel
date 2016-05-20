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
package org.apache.camel.component.telegram.model;


import java.io.IOException;
import java.time.Instant;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A deserializer for a unix timestamp.
 */
public class UnixTimestampDeserializer extends JsonDeserializer<Instant> {

    private Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public Instant deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        try {
            Long unixTimestamp = Long.parseLong(jsonParser.getText());

            return Instant.ofEpochSecond(unixTimestamp);
        } catch (Exception e) {
            log.warn("Unable to deserialize the unix timestamp " + jsonParser.getText(), e);
            return null;
        }
    }
}
