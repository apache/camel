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
package org.apache.camel.component.leveldb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.leveldb.serializer.JacksonLevelDBSerializer;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledOnOs({ OS.AIX, OS.OTHER })
public class LevelDBCustomSerializationTest extends CamelTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data");
        super.setUp();
    }

    @Test
    public void testLevelDBAggregate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        ObjectWithBinaryField objectA = new ObjectWithBinaryField("a", "byteArray1".getBytes());
        ObjectWithBinaryField objectB = new ObjectWithBinaryField("b", "byteArray2".getBytes());
        ObjectWithBinaryField objectC = new ObjectWithBinaryField("c", "byteArray3".getBytes());

        mock.expectedBodiesReceived(objectA.aggregateWith(objectB).aggregateWith(objectC).withA("a+b+c"));

        template.sendBodyAndHeader("direct:start", objectA, "id", 123);
        template.sendBodyAndHeader("direct:start", objectB, "id", 123);
        template.sendBodyAndHeader("direct:start", objectC, "id", 123);

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);

        // from endpoint should be preserved
        assertEquals("direct://start", mock.getReceivedExchanges().get(0).getFromEndpoint().getEndpointUri());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            // START SNIPPET: e1
            public void configure() {
                // create the leveldb repo
                LevelDBAggregationRepository repo = new LevelDBAggregationRepository("repo1", "target/data/leveldb.dat");

                SimpleModule simpleModule = new SimpleModule();
                simpleModule.addSerializer(ObjectWithBinaryField.class, new ObjectWithBinaryFieldSerializer());
                simpleModule.addDeserializer(ObjectWithBinaryField.class, new ObjectWithBinaryFieldDeserializer());

                repo.setSerializer(new JacksonLevelDBSerializer(simpleModule));

                // here is the Camel route where we aggregate
                from("direct:start")
                        .aggregate(header("id"), new MyAggregationStrategy())
                        // use our created leveldb repo as aggregation repository
                        .completionSize(3).aggregationRepository(repo)
                        .to("mock:aggregated");
            }
            // END SNIPPET: e1
        };
    }

    public static class MyAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }

            ObjectWithBinaryField oldObject = oldExchange.getIn().getBody(ObjectWithBinaryField.class);
            ObjectWithBinaryField newObject = newExchange.getIn().getBody(ObjectWithBinaryField.class);

            if (oldObject == null) {
                return newExchange;
            }

            try {
                oldExchange.getIn().setBody(oldObject.aggregateWith(newObject));
            } catch (IOException e) {
                //ignore
            }

            return oldExchange;
        }
    }

    public static class ObjectWithBinaryField implements Serializable {
        private String a;
        private byte[] b;

        public ObjectWithBinaryField() {
        }

        public ObjectWithBinaryField(String a, byte[] b) {
            this.a = a;
            this.b = b;
        }

        public ObjectWithBinaryField withA(String a) {
            this.a = a;
            return this;
        }

        public ObjectWithBinaryField aggregateWith(ObjectWithBinaryField newObject) throws IOException {

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                outputStream.write(b);
                outputStream.write(newObject.b);

                return new ObjectWithBinaryField(a + newObject.a, outputStream.toByteArray());
            }
        }

        @Override
        public String toString() {
            return "ObjectWithBinaryField{" +
                   "a='" + a + '\'' +
                   ", b=" + Arrays.toString(b) +
                   '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ObjectWithBinaryField that = (ObjectWithBinaryField) o;
            return Objects.equals(a, that.a) &&
                    Arrays.equals(b, that.b);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(a);
            result = 31 * result + Arrays.hashCode(b);
            return result;
        }
    }

    public static class ObjectWithBinaryFieldSerializer extends StdSerializer<ObjectWithBinaryField> {
        protected ObjectWithBinaryFieldSerializer() {
            super(ObjectWithBinaryField.class);
        }

        @Override
        public void serialize(ObjectWithBinaryField value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.a + "+:" + new String(value.b));
        }
    }

    public static class ObjectWithBinaryFieldDeserializer extends StdDeserializer<ObjectWithBinaryField> {
        protected ObjectWithBinaryFieldDeserializer() {
            super(ObjectWithBinaryField.class);
        }

        @Override
        public ObjectWithBinaryField deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            JsonNode treeNode = p.getCodec().readTree(p);

            String s = treeNode.textValue();

            return new ObjectWithBinaryField(s.substring(0, s.indexOf(":")), s.substring(s.indexOf(":") + 1).getBytes());
        }
    }
}
