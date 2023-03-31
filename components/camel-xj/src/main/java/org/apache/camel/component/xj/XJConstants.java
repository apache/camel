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

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonToken;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public final class XJConstants {

    /**
     * The namespace used by xj for typehints
     */
    public static final String NS_XJ = "http://camel.apache.org/component/xj";

    /**
     * The namespace prefix used by xj for typehints
     */
    public static final String NS_PREFIX_XJ = "xj";

    /**
     * Name typehint. Used to instruct xj to write a field with that name when converting to json. On the otherhand when
     * converting to xml xj writes the json field name in that attribute.
     */
    public static final String TYPE_HINT_NAME = "name";

    /**
     * JSON-Type hint. Used to instruct xj of which type the output is when converting to json. Otherwise when
     * converting to xml the attribute holds the type that was in the original json document.
     */
    public static final String TYPE_HINT_TYPE = "type";
    @Metadata(description = "The XSLT file name", javaType = "String")
    public static final String XSLT_FILE_NAME = Exchange.XSLT_FILE_NAME;

    /**
     * Mapping from json-types to typehint names
     */
    static final Map<JsonToken, String> JSONTYPE_TYPE_MAP;

    /**
     * Mapping from typehint names to json-types
     */
    static final Map<String, JsonToken> TYPE_JSONTYPE_MAP;

    static final String UNSUPPORTED_OPERATION_EXCEPTION_MESSAGE = "unsupported / not yet implemented";

    /**
     * Field name when xml contains mixed-content
     */
    static final String JSON_WRITER_MIXED_CONTENT_TEXT_KEY = "#text";

    static {
        final Map<JsonToken, String> jsonTypeTypeMap = new EnumMap<>(JsonToken.class);
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
