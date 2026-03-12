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
package org.apache.camel.component.leveldb.serializer.jackson;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.json.JsonMapper;

public class BodyDeserializer extends StdDeserializer<Object> {
    BodyDeserializer() {
        super(Object.class);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) {
        JsonNode treeNode = ctxt.readTree(p);
        ObjectMapper om = JsonMapper.builder().build();

        if (treeNode.get("clazz") != null) {
            Class<?> cl = om.readValue(treeNode.get("clazz").toString(), Class.class);
            return om.readValue(treeNode.get("data").toString(), cl);
        }

        return om.readValue(treeNode.toString(), Object.class);
    }

}
