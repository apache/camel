/**
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
package org.apache.camel.component.jsonvalidator;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.everit.json.schema.loader.SchemaLoader.SchemaLoaderBuilder;
import org.json.JSONObject;
import org.json.JSONTokener;

public class TestCustomSchemaLoader implements JsonSchemaLoader {

    @Override
    public Schema createSchema(CamelContext camelContext, InputStream schemaInputStream) throws IOException {

        SchemaLoaderBuilder schemaLoaderBuilder = SchemaLoader.builder().draftV6Support();

        try (InputStream inputStream = schemaInputStream) {
            JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
            return schemaLoaderBuilder.schemaJson(rawSchema).addFormatValidator(new EvenCharNumValidator()).build().load().build();
        }
    }

}
