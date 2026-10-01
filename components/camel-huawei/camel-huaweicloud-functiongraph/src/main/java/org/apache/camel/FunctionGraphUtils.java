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
package org.apache.camel;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.camel.models.ClientConfigurations;

public final class FunctionGraphUtils {

    private FunctionGraphUtils() {
    }

    /**
     * Gets the fieldName from the jsonString and returns it as a String
     *
     * @param  jsonString
     * @param  fieldName
     * @return
     */
    public static String extractJsonFieldAsString(String jsonString, String fieldName) {
        Gson gson = new Gson();
        JsonObject root = gson.fromJson(jsonString, JsonObject.class);
        if (root == null) {
            return null;
        }
        JsonElement field = root.get(fieldName);
        if (field == null || field.isJsonNull()) {
            return null;
        }
        // a FunctionGraph 'body' is commonly a JSON-encoded string/primitive (HTTP-triggered functions),
        // not always an object; return the raw value for primitives and the JSON text for objects/arrays
        return field.isJsonPrimitive() ? field.getAsString() : field.toString();
    }

    /**
     * Returns the urn based on urnFormat and clientConfigurations
     *
     * @param  urnFormat
     * @param  clientConfigurations
     * @return
     */
    public static String composeUrn(String urnFormat, ClientConfigurations clientConfigurations) {
        return String.format(urnFormat, clientConfigurations.getRegion(),
                clientConfigurations.getProjectId(), clientConfigurations.getFunctionPackage(),
                clientConfigurations.getFunctionName());
    }
}
