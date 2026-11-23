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
package org.apache.camel.dataformat.protobuf;

import com.google.protobuf.DynamicMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.protobuf.generated.AddressBookProtos;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The schema option without camel-protobuf-schema, which is not a dependency of camel-protobuf.
 */
public class ProtobufSchemaParserLookupTest {

    @Test
    public void schemaWithoutParserFailsAtStartup() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(route("message A { optional string x = 1; }"));
            Exception e = assertThrows(Exception.class, context::start);
            Throwable cause = e;
            while (cause.getCause() != null && !(cause instanceof IllegalStateException)) {
                cause = cause.getCause();
            }
            assertInstanceOf(IllegalStateException.class, cause, e.toString());
            assertTrue(cause.getMessage().contains("add camel-protobuf-schema"), cause.getMessage());
        }
    }

    @Test
    public void parserFromRegistry() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            // a parser of the registry, here one that knows a single compiled message type
            ProtobufSchemaParser parser = (schema, messageName) -> AddressBookProtos.Person.getDescriptor();
            context.getRegistry().bind("myParser", parser);
            context.addRoutes(route("any text, the parser above does not read it"));
            context.start();
            Object body = context.createProducerTemplate().requestBody("direct:start",
                    "{\"name\":\"Ann\",\"id\":1}");
            DynamicMessage person = assertInstanceOf(DynamicMessage.class, body);
            assertEquals("Ann", person.getField(AddressBookProtos.Person.getDescriptor().findFieldByName("name")));
        }
    }

    private static RouteBuilder route(String schema) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                ProtobufDataFormat format = new ProtobufDataFormat();
                format.setSchema(schema);
                format.setContentTypeFormat("json");
                from("direct:start").unmarshal(format);
            }
        };
    }
}
