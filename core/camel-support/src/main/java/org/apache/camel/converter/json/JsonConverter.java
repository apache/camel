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
package org.apache.camel.converter.json;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsonable;
import org.apache.camel.util.json.Jsoner;

/**
 * A set of {@link Converter} methods for camel-util-json objects.
 */
@Converter(generateBulkLoader = true)
public final class JsonConverter {

    /**
     * Utility classes should not have a public constructor.
     */
    private JsonConverter() {
    }

    @Converter(order = 1)
    public static JsonObject convertToJsonObject(String json, Exchange exchange) throws Exception {
        return Jsoner.deserialize(json, (JsonObject) null);
    }

    @Converter(order = 2)
    public static JsonArray convertToJsonArray(String json, Exchange exchange) throws Exception {
        return (JsonArray) Jsoner.deserialize(json);
    }

    @Converter(order = 3)
    public static Jsonable convertToJson(String json, Exchange exchange) throws Exception {
        return (Jsonable) Jsoner.deserialize(json);
    }

}
