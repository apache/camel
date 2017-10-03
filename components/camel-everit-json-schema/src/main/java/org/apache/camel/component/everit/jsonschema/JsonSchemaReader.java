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
        if ( this.schema == null ) {
            this.schema = this.schemaLoader.createSchema(this.camelContext, this.resourceUri);
        }
        return schema;
    }
    
    public void setSchema(Schema schema) {
        this.schema = schema;
    }
}
