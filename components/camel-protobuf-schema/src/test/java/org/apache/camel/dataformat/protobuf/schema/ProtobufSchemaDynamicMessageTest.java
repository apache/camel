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
package org.apache.camel.dataformat.protobuf.schema;

import java.util.Map;

import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.protobuf.ProtobufDataFormat;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The schema option of the protobuf data format, with the parser of camel-protobuf-schema found on the classpath.
 */
public class ProtobufSchemaDynamicMessageTest extends CamelTestSupport {

    private static final String JSON
            = "{\"id\":\"ORD-1\",\"quantity\":2,\"lines\":[{\"sku\":\"A\",\"price\":2.5}],\"status\":\"PAID\","
              + "\"tags\":{\"k\":\"v\"},\"created\":\"2026-11-21T10:00:00Z\",\"invoice\":\"INV-9\"}";

    @Test
    public void jsonToDynamicMessage() throws Exception {
        Object body = template.requestBody("direct:fromJson", JSON);
        DynamicMessage order = assertInstanceOf(DynamicMessage.class, body);
        assertEquals("shop.Order", order.getDescriptorForType().getFullName());
        assertEquals(JSON, JsonFormat.printer().omittingInsignificantWhitespace().print(order));
    }

    @Test
    public void inlineSchemaAndMessageName() throws Exception {
        Object body = template.requestBody("direct:inline", "{\"sku\":\"B\",\"price\":1.0}");
        DynamicMessage line = assertInstanceOf(DynamicMessage.class, body);
        assertEquals("Line", line.getDescriptorForType().getName());
    }

    @Test
    public void mapToBinaryAndBack() throws Exception {
        byte[] bytes = template.requestBody("direct:toBinary", Map.of("id", "ORD-2", "quantity", 3), byte[].class);
        Object body = template.requestBody("direct:fromBinary", bytes);
        DynamicMessage order = assertInstanceOf(DynamicMessage.class, body);
        assertEquals("{\"id\":\"ORD-2\",\"quantity\":3}", JsonFormat.printer().omittingInsignificantWhitespace().print(order));
    }

    @Test
    public void schemaAndMessageNameOfTheRouteModel() throws Exception {
        Object body = template.requestBody("direct:model", "{\"sku\":\"C\",\"price\":3.5}");
        DynamicMessage line = assertInstanceOf(DynamicMessage.class, body);
        assertEquals("shop.Order.Line", line.getDescriptorForType().getFullName());
        assertEquals("{\"sku\":\"C\",\"price\":3.5}", JsonFormat.printer().omittingInsignificantWhitespace().print(line));
    }

    @Test
    public void unknownMessageNameFails() {
        assertThrows(IllegalArgumentException.class,
                () -> new WireProtobufSchemaParser().parse("syntax = \"proto3\"; message A { string x = 1; }", "B"));
    }

    @Test
    public void otherImportFails() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new WireProtobufSchemaParser().parse(
                        "syntax = \"proto3\"; import \"other.proto\"; message A { string x = 1; }",
                        null));
        assertTrue(e.getMessage().contains("other.proto"), e.getMessage());
    }

    @Test
    public void syntaxErrorFails() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new WireProtobufSchemaParser().parse("message A { string x = 1; }", null));
        assertTrue(e.getMessage().startsWith("Cannot read the protobuf schema"), e.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                ProtobufDataFormat json = new ProtobufDataFormat();
                json.setSchema("classpath:order.proto");
                json.setContentTypeFormat("json");
                from("direct:fromJson").unmarshal(json);

                ProtobufDataFormat inline = new ProtobufDataFormat();
                inline.setSchema("syntax = \"proto3\"; package shop; message Order { repeated Line lines = 1;"
                                 + " message Line { string sku = 1; double price = 2; } }");
                inline.setMessageName("shop.Order.Line");
                inline.setContentTypeFormat("json");
                from("direct:inline").unmarshal(inline);

                ProtobufDataFormat binary = new ProtobufDataFormat();
                binary.setSchema("classpath:order.proto");
                from("direct:toBinary").marshal(binary);
                from("direct:fromBinary").unmarshal(binary);

                // the options of the model (as in XML and YAML routes), set on the data format by the reifier
                from("direct:model").unmarshal(dataFormat().protobuf().schema("classpath:order.proto")
                        .messageName("Order.Line").contentTypeFormat("json").end());
            }
        };
    }
}
