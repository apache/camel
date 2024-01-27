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
package org.apache.camel.component.jsonvalidator;

import java.io.*;
import java.net.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.SpecVersionDetector;
import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;

public class DefaultJsonUriSchemaLoader implements JsonUriSchemaLoader {

    protected ObjectMapper mapper = new ObjectMapper();

    protected SchemaValidatorsConfig config = new SchemaValidatorsConfig();

    protected SpecVersion.VersionFlag defaultVersion = SpecVersion.VersionFlag.V201909;

    @Override
    public JsonSchema createSchema(CamelContext camelContext, String schemaUri) throws Exception {
        // determine schema version
        InputStream stream = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, schemaUri);
        JsonNode node = mapper.readTree(stream);
        SpecVersion.VersionFlag version;
        try {
            version = SpecVersionDetector.detect(node);
        } catch (JsonSchemaException e) {
            // default if no schema version was specified
            version = defaultVersion;
        }

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(version);

        // the URI based method will correctly resolve relative schema references to other schema in the same directory
        URI uri;
        if (ResourceHelper.hasScheme(schemaUri)) {
            uri = URI.create(schemaUri);
        } else {
            uri = URI.create("classpath:" + schemaUri);
        }
        return factory.getSchema(uri, node, config);
    }

}
