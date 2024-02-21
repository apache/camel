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

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.impl.AsPropertyTypeDeserializer;
import com.fasterxml.jackson.databind.node.TreeTraversingParser;

public class AsNestedPropertyDeserializer extends AsPropertyTypeDeserializer {

    public AsNestedPropertyDeserializer(JavaType bt, TypeIdResolver idRes, String typePropertyName, boolean typeIdVisible,
                                        JavaType defaultImpl, JsonTypeInfo.As inclusion) {
        super(bt, idRes, typePropertyName, typeIdVisible, defaultImpl, inclusion);
    }

    public AsNestedPropertyDeserializer(AsPropertyTypeDeserializer src, BeanProperty property) {
        super(src, property);
    }

    @Override
    public TypeDeserializer forProperty(BeanProperty prop) {
        return (prop == _property) ? this : new AsNestedPropertyDeserializer(this, prop);
    }

    @Override
    public Object deserializeTypedFromObject(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode originalNode = p.readValueAsTree();
        JsonNode node = originalNode;
        for (String property : _typePropertyName.split("\\.")) {
            JsonNode nestedProperty = node.get(property);
            if (nestedProperty == null) {
                ctxt.reportInputMismatch(_property,
                        "Nested property not found in JSON: " + _typePropertyName);
                return null;
            }
            node = nestedProperty;
        }
        JsonDeserializer<Object> deser = _findDeserializer(ctxt, node.asText());
        JsonParser jsonParser = new TreeTraversingParser(originalNode, p.getCodec());
        if (jsonParser.getCurrentToken() == null) {
            jsonParser.nextToken();
        }
        return deser.deserialize(jsonParser, ctxt);
    }
}
