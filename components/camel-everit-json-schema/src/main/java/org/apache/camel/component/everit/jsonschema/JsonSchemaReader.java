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
package org.apache.camel.component.everit.jsonschema;

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.util.ObjectHelper;
import org.everit.json.schema.Schema;

public class JsonSchemaReader {    
    private Schema schema;
    
    private final CamelContext camelContext;
    private final String resourceUri;
    private final JsonSchemaLoader schemaLoader;
    
    public JsonSchemaReader(CamelContext camelContext, String resourceUri, JsonSchemaLoader schemaLoader) {
        ObjectHelper.notNull(camelContext, "camelContext");
        ObjectHelper.notNull(resourceUri, "resourceUri");
        ObjectHelper.notNull(schemaLoader, "schemaLoader");

        this.camelContext = camelContext;
        this.resourceUri = resourceUri;
        this.schemaLoader = schemaLoader;
    }
    
    public Schema getSchema() throws IOException {
        if (this.schema == null) {
            this.schema = this.schemaLoader.createSchema(this.camelContext, this.resourceUri);
        }
        return schema;
    }
    
    public void setSchema(Schema schema) {
        this.schema = schema;
    }
}
