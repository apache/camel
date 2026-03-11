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
package org.apache.camel.groovy.json;

import java.util.List;

import groovy.util.Node;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsonable;
import org.apache.groovy.json.internal.LazyMap;

@Converter(generateBulkLoader = true)
public class GroovyJsonConverter {

    /**
     * Utility classes should not have a public constructor.
     */
    private GroovyJsonConverter() {
    }

    @Converter(order = 1)
    public static JsonObject convertToJsonObject(Node node, Exchange exchange) throws Exception {
        return NodeToJsonHelper.nodeToJson(node);
    }

    @Converter(order = 2)
    public static Jsonable convertToJson(Node node, Exchange exchange) throws Exception {
        return NodeToJsonHelper.nodeToJson(node);
    }

    @Converter(order = 3)
    public static JsonObject convertToJsonObject(LazyMap node, Exchange exchange) throws Exception {
        return new JsonObject(node);
    }

    @Converter(order = 4)
    public static JsonArray convertToJsonArray(List<?> node, Exchange exchange) throws Exception {
        return new JsonArray(node);
    }

    @Converter(order = 5)
    public static Jsonable convertToJson(LazyMap node, Exchange exchange) throws Exception {
        return new JsonObject(node);
    }

    @Converter(order = 6)
    public static Jsonable convertToJson(List<?> node, Exchange exchange) throws Exception {
        return new JsonArray(node);
    }

}
