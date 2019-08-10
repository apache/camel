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

package org.apache.camel.component.xj;

import com.fasterxml.jackson.core.JsonToken;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class XJConstants {

    public static final String NS_XJ = "http://camel.apache.org/component/xj";
    public static final String NS_PREFIX_XJ = "xj";

    public static final String TYPE_HINT_NAME = "name";
    public static final String TYPE_HINT_TYPE = "type";

    public static final Map<JsonToken, String> JSONTYPE_TYPE_MAP;
    public static final Map<String, JsonToken> TYPE_JSONTYPE_MAP;

    public static final String UNSUPPORTED_OPERATION_EXCEPTION_MESSAGE = "unsupported / not yet implemented";

    static {
        final Map<JsonToken, String> jsonTypeTypeMap = new HashMap<>();
        jsonTypeTypeMap.put(JsonToken.START_OBJECT, "object");
        jsonTypeTypeMap.put(JsonToken.END_OBJECT, "object");
        jsonTypeTypeMap.put(JsonToken.START_ARRAY, "array");
        jsonTypeTypeMap.put(JsonToken.END_ARRAY, "array");
        jsonTypeTypeMap.put(JsonToken.VALUE_STRING, "string");
        jsonTypeTypeMap.put(JsonToken.VALUE_NUMBER_INT, "int");
        jsonTypeTypeMap.put(JsonToken.VALUE_NUMBER_FLOAT, "float");
        jsonTypeTypeMap.put(JsonToken.VALUE_TRUE, "boolean");
        jsonTypeTypeMap.put(JsonToken.VALUE_FALSE, "boolean");
        jsonTypeTypeMap.put(JsonToken.VALUE_NULL, "null");

        JSONTYPE_TYPE_MAP = Collections.unmodifiableMap(jsonTypeTypeMap);

        final Map<String, JsonToken> typeJsonTypeMap = new HashMap<>();
        typeJsonTypeMap.put("object", JsonToken.START_OBJECT);
        typeJsonTypeMap.put("array", JsonToken.START_ARRAY);
        typeJsonTypeMap.put("string", JsonToken.VALUE_STRING);
        typeJsonTypeMap.put("int", JsonToken.VALUE_NUMBER_INT);
        typeJsonTypeMap.put("float", JsonToken.VALUE_NUMBER_FLOAT);
        typeJsonTypeMap.put("boolean", JsonToken.VALUE_TRUE);
        typeJsonTypeMap.put("null", JsonToken.VALUE_NULL);

        TYPE_JSONTYPE_MAP = Collections.unmodifiableMap(typeJsonTypeMap);
    }

    private XJConstants() {
    }
}
