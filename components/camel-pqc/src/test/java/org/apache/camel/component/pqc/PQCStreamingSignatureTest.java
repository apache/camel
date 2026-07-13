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
package org.apache.camel.component.pqc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PQCStreamingSignatureTest extends CamelTestSupport {

    @EndpointInject("mock:verify")
    protected MockEndpoint resultVerify;

    @BeforeAll
    public static void startup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // Exercise the re-readable StreamCache path (sign then verify in the same route)
        context.setStreamCaching(true);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:signOnly").to("pqc:sign?operation=sign&signatureAlgorithm=MLDSA");
                from("direct:verifyOnly").to("pqc:verify?operation=verify&signatureAlgorithm=MLDSA");
                from("direct:signVerify")
                        .to("pqc:sign?operation=sign&signatureAlgorithm=MLDSA")
                        .to("pqc:verify?operation=verify&signatureAlgorithm=MLDSA")
                        .to("mock:verify");
            }
        };
    }

    private byte[] sign(Object body) {
        Exchange out = template.request("direct:signOnly", e -> e.getMessage().setBody(body));
        return out.getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class);
    }

    private boolean verify(Object body, byte[] signature) {
        Exchange out = template.request("direct:verifyOnly", e -> {
            e.getMessage().setBody(body);
            e.getMessage().setHeader(PQCConstants.SIGNATURE, signature);
        });
        return out.getMessage().getHeader(PQCConstants.VERIFY, Boolean.class);
    }

    @Test
    void testByteArrayRoundTrip() {
        byte[] content = "hello streaming world".getBytes(StandardCharsets.UTF_8);
        byte[] sig = sign(content);
        assertNotNull(sig);
        assertTrue(verify(content, sig));
    }

    @Test
    void testSignInputStreamVerifyBytes() {
        byte[] content = "signed from an input stream".getBytes(StandardCharsets.UTF_8);
        byte[] sig = sign(new ByteArrayInputStream(content));
        assertNotNull(sig);
        assertTrue(verify(content, sig), "a streamed sign must verify against the same bytes");
    }

    @Test
    void testSignBytesVerifyInputStream() {
        byte[] content = "verified from an input stream".getBytes(StandardCharsets.UTF_8);
        byte[] sig = sign(content);
        assertTrue(verify(new ByteArrayInputStream(content), sig), "a streamed verify must accept the matching bytes");
    }

    @Test
    void testBinaryPayloadNotCorrupted() {
        // Bytes that are not valid UTF-8: a String round-trip would corrupt them and break verification
        byte[] binary = new byte[512];
        for (int i = 0; i < binary.length; i++) {
            binary[i] = (byte) (i % 256);
        }
        assertTrue(verify(binary, sign(binary)));
        assertTrue(verify(binary, sign(new ByteArrayInputStream(binary))));
    }

    @Test
    void testLargeStreamSignVerifyInOneRoute() throws Exception {
        byte[] large = new byte[1024 * 1024]; // 1 MB
        for (int i = 0; i < large.length; i++) {
            large[i] = (byte) i;
        }
        resultVerify.expectedMessageCount(1);
        template.sendBody("direct:signVerify", new ByteArrayInputStream(large));
        resultVerify.assertIsSatisfied();
        assertTrue(resultVerify.getExchanges().get(0).getMessage().getHeader(PQCConstants.VERIFY, Boolean.class),
                "large streamed payload should sign and verify within one route");
    }
}
