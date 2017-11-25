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
import java.io.InputStreamReader;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.cfg.ValidationConfiguration;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.library.DraftV4Library;
import com.github.fge.jsonschema.library.Library;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.messages.JsonSchemaValidationBundle;
import com.github.fge.msgsimple.bundle.MessageBundle;
import com.github.fge.msgsimple.load.MessageBundles;
import com.github.fge.msgsimple.source.MapMessageSource;
import com.github.fge.msgsimple.source.MessageSource;

import org.apache.camel.CamelContext;

public class TestCustomSchemaLoader implements JsonSchemaLoader {

    @Override
    public JsonSchema createSchema(CamelContext camelContext, InputStream schemaInputStream) throws IOException, ProcessingException {

        JsonNode schemaNode = JsonLoader.fromReader(new InputStreamReader(schemaInputStream));
        
        /*
         * Build a new library with our added format attribute
         */
        final Library library = DraftV4Library.get().thaw()
            .addFormatAttribute("evenlength", EvenCharNumValidator.getInstance())
            .freeze();

        /*
         * Build a new message bundle with our added error message
         */
        final String key = "evenlength";
        final String value = "oddlength";
        final MessageSource source = MapMessageSource.newBuilder()
            .put(key, value).build();
        final MessageBundle bundle
            = MessageBundles.getBundle(JsonSchemaValidationBundle.class)
            .thaw().appendSource(source).freeze();

        /*
         * Build our dedicated validation configuration
         */
        final ValidationConfiguration cfg = ValidationConfiguration.newBuilder()
            .setDefaultLibrary("http://json-schema.org/draft-06/schema#", library)
            .setValidationMessages(bundle).freeze();

        final JsonSchemaFactory factory = JsonSchemaFactory.newBuilder()
            .setValidationConfiguration(cfg).freeze();

        final JsonSchema schema = factory.getJsonSchema(schemaNode);
        
        return schema;
    }

}
