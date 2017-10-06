package org.apache.camel.component.everit.jsonschema;

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
    public Schema createSchema(CamelContext camelContext, InputStream schemaInputStream)
            throws IOException {
        
        SchemaLoaderBuilder schemaLoaderBuilder = SchemaLoader.builder().draftV6Support();
        
        try (InputStream inputStream = schemaInputStream) {
            JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
            return schemaLoaderBuilder
                    .schemaJson(rawSchema)
                    .addFormatValidator(new EvenCharNumValidator())
                    .build()
                    .load()
                    .build();
        }
    }

}
