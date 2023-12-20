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

package org.apache.camel.component.jackson.protobuf.transform;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import org.apache.camel.Exchange;
import org.apache.camel.component.jackson.SchemaHelper;
import org.apache.camel.component.jackson.transform.Json;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.TransformerKey;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProtobufStructDataTypeTransformerTest {
    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final ProtobufStructDataTypeTransformer transformer = new ProtobufStructDataTypeTransformer();

    @Test
    void shouldHandleProtobufBinary() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        ProtobufSchema protobufSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, protobufSchema);
        exchange.getMessage().setBody(Protobuf.mapper().writer(protobufSchema)
                .writeValueAsBytes(new Person("Christoph", 32)));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(ObjectNode.class, exchange.getMessage().getBody().getClass());
    }

    @Test
    void shouldHandleJsonString() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        ProtobufSchema protobufSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, protobufSchema);
        exchange.getMessage().setBody("""
                    { "name": "Christoph", "age": 32 }
                """);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(ObjectNode.class, exchange.getMessage().getBody().getClass());
    }

    @Test
    void shouldHandlePojo() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        ProtobufSchema protobufSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, protobufSchema);
        exchange.getMessage().setBody(new Person("Mickey", 20));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(ObjectNode.class, exchange.getMessage().getBody().getClass());
    }

    @Test
    void shouldHandleJsonNode() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        ProtobufSchema protobufSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, protobufSchema);
        exchange.getMessage().setBody(Json.mapper().readerFor(JsonNode.class).readValue("""
                    { "name": "Goofy", "age": 25 }
                """));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(ObjectNode.class, exchange.getMessage().getBody().getClass());
    }

    @Test
    void shouldHandleExplicitContentClass() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        ProtobufSchema protobufSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, protobufSchema);
        exchange.setProperty(SchemaHelper.CONTENT_CLASS, Person.class.getName());
        exchange.getMessage().setBody(new Person("Donald", 19));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(ObjectNode.class, exchange.getMessage().getBody().getClass());
    }

    @Test
    public void shouldLookupDataTypeTransformer() throws Exception {
        Transformer transformer = camelContext.getTransformerRegistry()
                .resolveTransformer(new TransformerKey("protobuf-x-struct"));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(ProtobufStructDataTypeTransformer.class, transformer.getClass());
    }

    private ProtobufSchema getSchema() throws IOException {
        return Protobuf.mapper().schemaLoader()
                .load(ProtobufStructDataTypeTransformer.class.getResourceAsStream("Person.proto"));
    }
}
