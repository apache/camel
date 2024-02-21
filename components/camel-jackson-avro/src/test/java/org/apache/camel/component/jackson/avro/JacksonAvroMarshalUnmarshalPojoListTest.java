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
package org.apache.camel.component.jackson.avro;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import org.apache.avro.Schema;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.SchemaResolver;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JacksonAvroMarshalUnmarshalPojoListTest extends CamelTestSupport {

    @Test
    public void testMarshalUnmarshalPojoList() throws Exception {
        MockEndpoint mock1 = getMockEndpoint("mock:serialized");
        mock1.expectedMessageCount(1);

        List<Pojo> pojos = new ArrayList<>();
        pojos.add(new Pojo("Hello"));
        pojos.add(new Pojo("World"));

        template.sendBody("direct:pojo", pojos);

        mock1.assertIsSatisfied();

        byte[] serialized = mock1.getReceivedExchanges().get(0).getIn().getBody(byte[].class);
        assertNotNull(serialized);
        assertEquals(14, serialized.length);

        MockEndpoint mock2 = getMockEndpoint("mock:pojo");
        mock2.expectedMessageCount(1);
        mock2.message(0).body().isInstanceOf(List.class);

        template.sendBody("direct:serialized", serialized);
        mock2.assertIsSatisfied();

        @SuppressWarnings("unchecked")
        List<Pojo> back = mock2.getReceivedExchanges().get(0).getIn().getBody(List.class);

        assertEquals(2, back.size());
        assertEquals("Hello", back.get(0).getText());
        assertEquals("World", back.get(1).getText());
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        String schemaJson = "{\n" +
                            "  \"type\": \"array\",  \n" +
                            "  \"items\":{\n" +
                            "    \"name\":\"Pojo\",\n" +
                            "    \"type\":\"record\",\n" +
                            "    \"fields\":[\n" +
                            "      {\"name\":\"text\", \"type\":\"string\"}\n" +
                            "    ]\n" +
                            "  }\n" +
                            "}";
        Schema raw = new Schema.Parser().setValidate(true).parse(schemaJson);
        AvroSchema schema = new AvroSchema(raw);
        SchemaResolver resolver = ex -> schema;
        registry.bind("schema-resolver", SchemaResolver.class, resolver);

        JacksonAvroDataFormat df = new JacksonAvroDataFormat();
        df.setUnmarshalType(Pojo.class);
        df.setUseList(true);
        registry.bind("custom-df", DataFormat.class, df);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:serialized").unmarshal().custom("custom-df").to("mock:pojo");
                from("direct:pojo").marshal().custom("custom-df").to("mock:serialized");
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
