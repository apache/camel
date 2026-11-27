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
package org.apache.camel.component.avro;

import org.apache.avro.Protocol;
import org.apache.camel.BindToRegistry;
import org.apache.camel.avro.generated.Key;
import org.apache.camel.avro.generated.Value;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consumers on the same host and port share one Avro server, which serves the transport, protocol and responder type of
 * the first consumer. A consumer that does not match fails to start, and does not break the consumer that serves.
 */
public class AvroConsumerSharedServerTest extends AvroTestSupport {

    private static final String KEY_VALUE = "protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol";

    @BindToRegistry("otherProtocol")
    private final Protocol otherProtocol = Protocol.parse(
            "{\"namespace\": \"org.example.other\", \"protocol\": \"OtherProtocol\", \"types\": [],"
                                                          + " \"messages\": {\"ping\": {\"request\": [], \"response\": \"string\"}}}");

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("avro:netty:localhost:" + avroPort + "?" + KEY_VALUE).routeId("default")
                        .process(e -> e.getMessage().setBody(value("default")));

                from("direct:get").to("avro:netty:localhost:" + avroPort + "/get?" + KEY_VALUE);

                from("avro:netty:localhost:" + avroPortReflection + "/get?protocolLocation=file:src/test/avro/test.avpr")
                        .process(e -> {
                            Object[] params = e.getIn().getBody(Object[].class);
                            e.getMessage().setBody(value("location-" + ((Key) params[0]).getKey()));
                        });

                from("direct:getLocation")
                        .to("avro:netty:localhost:" + avroPortReflection
                            + "/get?protocolLocation=file:src/test/avro/test.avpr");
            }
        };
    }

    // the Avro Netty server takes a few seconds to close, so each test method checks several cases

    @Test
    public void consumersThatCannotShareTheServerFailAndTheFirstStillServes() {
        // the same host and port written as netty://, so Camel does not see the same endpoint URI
        assertConsumerFailsToStart("avro:netty://localhost:" + avroPort + "?" + KEY_VALUE,
                "Default consumer already registered");
        assertServes();

        // a default consumer: when it fails, it must not unregister the default consumer of the netty server
        assertConsumerFailsToStart("avro:http:localhost:" + avroPort + "?" + KEY_VALUE, "the transport http differs");
        assertServes();

        assertConsumerFailsToStart("avro:netty:localhost:" + avroPort + "/ping?protocol=#otherProtocol",
                "the protocol org.example.other.OtherProtocol differs");
        assertServes();

        assertConsumerFailsToStart("avro:netty:localhost:" + avroPort + "/put?" + KEY_VALUE + "&reflectionProtocol=true",
                "reflectionProtocol=true differs");
        assertServes();
    }

    @Test
    public void protocolLocation() {
        Value value = template.requestBody("direct:getLocation", key("k1"), Value.class);
        assertEquals("location-k1", value.getValue().toString());

        Exception e = assertThrows(Exception.class, () -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:missing")
                        .to("avro:netty:localhost:" + avroPort + "/get?protocolLocation=file:target/missing.avpr");
            }
        }));
        assertTrue(e.getMessage().contains("missing.avpr"), e.getMessage());
    }

    private void assertServes() {
        Value value = template.requestBody("direct:get", key("k1"), Value.class);
        assertEquals("default", value.getValue().toString());
    }

    private void assertConsumerFailsToStart(String uri, String reason) {
        Exception e = assertThrows(Exception.class, () -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("second").to("log:second");
            }
        }));
        AvroComponentException cause = ObjectHelper.getException(AvroComponentException.class, e);
        assertNotNull(cause, "AvroComponentException expected as cause of " + e);
        assertTrue(cause.getMessage().contains(reason), cause.getMessage());
    }

    private static Key key(String key) {
        return Key.newBuilder().setKey(key).build();
    }

    private static Value value(String value) {
        return Value.newBuilder().setValue(value).build();
    }
}
