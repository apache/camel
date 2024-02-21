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
package org.apache.camel.component.jackson.protobuf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.SchemaResolver;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.dataformat.ProtobufLibrary;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JacksonProtobufMarshalUnmarshalJsonNodeTest extends CamelTestSupport {

    @Test
    public void testMarshalUnmarshalJsonNode() throws Exception {
        MockEndpoint mock1 = getMockEndpoint("mock:serialized");
        mock1.expectedMessageCount(1);

        Pojo pojo = new Pojo("Hello");
        template.sendBody("direct:pojo", pojo);

        mock1.assertIsSatisfied();

        byte[] serialized = mock1.getReceivedExchanges().get(0).getIn().getBody(byte[].class);
        assertNotNull(serialized);
        assertEquals(7, serialized.length);

        MockEndpoint mock2 = getMockEndpoint("mock:pojo");
        mock2.expectedMessageCount(1);
        mock2.message(0).body().isInstanceOf(JsonNode.class);

        template.sendBody("direct:serialized", serialized);
        mock2.assertIsSatisfied();

        JsonNode back = mock2.getReceivedExchanges().get(0).getIn().getBody(JsonNode.class);

        assertEquals(pojo.getText(), back.at("/text").asText());
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        String protobufStr = "message Pojo {\n"
                             + " required string text = 1;\n"
                             + "}\n";
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(protobufStr);
        SchemaResolver resolver = ex -> schema;
        registry.bind("schema-resolver", SchemaResolver.class, resolver);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:serialized").unmarshal().protobuf(ProtobufLibrary.Jackson, JsonNode.class).to("mock:pojo");
                from("direct:pojo").marshal().protobuf(ProtobufLibrary.Jackson).to("mock:serialized");
            }
        };
    }

    public static class Pojo {

        private String text;

        public Pojo() {
        }

        public Pojo(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

}
