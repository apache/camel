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
package org.apache.camel.dsl.jbang.core.commands.validate;

import java.util.List;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * The result of validating a file in JSON mode, with the keys the MCP tool camel_validate_source answers with.
 */
final class ValidateJson {

    private ValidateJson() {
    }

    static JsonObject file(String file, List<String> errors) {
        JsonObject jo = new JsonObject();
        jo.put("valid", errors.isEmpty());
        jo.put("file", file);
        jo.put("errors", new JsonArray(errors));
        return jo;
    }
}
