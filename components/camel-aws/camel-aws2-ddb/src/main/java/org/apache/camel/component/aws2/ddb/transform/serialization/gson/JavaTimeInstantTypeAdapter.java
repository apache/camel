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

package org.apache.camel.component.aws2.ddb.transform.serialization.gson;

import java.lang.reflect.Type;
import java.time.Instant;

import com.google.gson.*;

public class JavaTimeInstantTypeAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {

    @Override
    public JsonElement serialize(
            final Instant time, final Type typeOfSrc,
            final JsonSerializationContext context) {
        return new JsonPrimitive(time.getEpochSecond() * 1000);
    }

    @Override
    public Instant deserialize(
            final JsonElement json, final Type typeOfT,
            final JsonDeserializationContext context)
            throws JsonParseException {
        return Instant.ofEpochMilli(json.getAsLong());
    }
}
