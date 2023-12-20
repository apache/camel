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

package org.apache.camel.component.jackson.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.Exchange;
import org.apache.camel.component.jackson.SchemaHelper;
import org.apache.camel.component.jackson.SchemaType;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JsonSchemaResolverTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final String person = """
                { "name": "Daisy", "age": 22 }
            """;

    @Test
    void shouldReadSchemaFromExchangeProperty() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.setProperty(SchemaHelper.CONTENT_CLASS, Person.class.getName());

        JsonNode schema
                = Json.mapper().readerFor(JsonNode.class).readValue(this.getClass().getResourceAsStream("person.schema.json"));
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, schema);
        exchange.getMessage().setBody(person);

        JsonSchemaResolver schemaResolver = new JsonSchemaResolver();
        schemaResolver.process(exchange);

        Assertions.assertEquals(schema, exchange.getProperty(SchemaHelper.CONTENT_SCHEMA));
        Assertions.assertEquals(SchemaType.JSON.type(), exchange.getProperty(SchemaHelper.CONTENT_SCHEMA_TYPE));
        Assertions.assertEquals(Person.class.getName(), exchange.getProperty(SchemaHelper.CONTENT_CLASS));
    }

    @Test
    void shouldReadSchemaFromSchema() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.setProperty(SchemaHelper.CONTENT_CLASS, Person.class.getName());

        String schemaString = new String(this.getClass().getResourceAsStream("person.schema.json").readAllBytes());
        exchange.setProperty(SchemaHelper.SCHEMA, schemaString);
        exchange.getMessage().setBody(person);

        JsonSchemaResolver schemaResolver = new JsonSchemaResolver();
        schemaResolver.process(exchange);

        Assertions.assertNotNull(exchange.getProperty(SchemaHelper.CONTENT_SCHEMA));
        Assertions.assertEquals(ObjectNode.class, exchange.getProperty(SchemaHelper.CONTENT_SCHEMA).getClass());
        Assertions.assertEquals(SchemaType.JSON.type(), exchange.getProperty(SchemaHelper.CONTENT_SCHEMA_TYPE));
        Assertions.assertEquals(Person.class.getName(), exchange.getProperty(SchemaHelper.CONTENT_CLASS));
    }

    @Test
    void shouldReadSchemaFromClasspathResource() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.setProperty(SchemaHelper.CONTENT_CLASS, Person.class.getName());
        exchange.getMessage().setBody(person);

        JsonSchemaResolver schemaResolver = new JsonSchemaResolver();
        schemaResolver.process(exchange);

        Assertions.assertNotNull(exchange.getProperty(SchemaHelper.CONTENT_SCHEMA));
        Assertions.assertEquals(ObjectNode.class, exchange.getProperty(SchemaHelper.CONTENT_SCHEMA).getClass());
        Assertions.assertEquals(SchemaType.JSON.type(), exchange.getProperty(SchemaHelper.CONTENT_SCHEMA_TYPE));
        Assertions.assertEquals(Person.class.getName(), exchange.getProperty(SchemaHelper.CONTENT_CLASS));
    }
}
