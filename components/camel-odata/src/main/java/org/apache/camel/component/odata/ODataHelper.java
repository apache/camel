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
package org.apache.camel.component.odata;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;

import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.Jsoner;

public final class ODataHelper {

    private ODataHelper() {
    }

    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof String string) {
            return string;
        }

        return Jsoner.serialize(value);
    }

    /**
     * Whether the body is a payload that holds the JSON text as it is: bytes, a stream or a file. It is read as text
     * and sent as it is, as a String body is, and not serialized to JSON.
     */
    public static boolean isPayload(Object value) {
        return value instanceof byte[]
                || value instanceof InputStream || value instanceof Reader || value instanceof StreamCache
                || value instanceof File || value instanceof Path || value instanceof WrappedFile;
    }

    /**
     * Parses the JSON of a response: a JSON object is a Map, a JSON array a List, and a plain value a number, a String
     * or a boolean.
     */
    public static Object parseJson(String value) throws DeserializationException {
        if (value == null || value.isBlank()) {
            return null;
        }

        return Jsoner.deserialize(value);
    }
}
