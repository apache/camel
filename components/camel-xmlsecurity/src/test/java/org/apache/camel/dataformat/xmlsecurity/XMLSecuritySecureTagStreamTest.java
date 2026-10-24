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
package org.apache.camel.dataformat.xmlsecurity;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.crypto.KeyGenerator;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The XPath of secureTag is evaluated on the document the data format has parsed, so a message body that is a stream
 * without stream caching is not read twice.
 */
class XMLSecuritySecureTagStreamTest extends CamelTestSupport {

    private static final String XML = "<order><id>1</id><name>Camel</name></order>";

    private byte[] passPhrase;

    @Override
    protected void doPreSetup() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        passPhrase = keyGenerator.generateKey().getEncoded();
    }

    @Test
    void secureTagElementWithStreamBody() {
        String encrypted = template.requestBody("direct:encryptElement", stream(XML), String.class);
        assertTrue(encrypted.contains("EncryptedData"), encrypted);
        assertTrue(encrypted.contains("http://www.w3.org/2001/04/xmlenc#Element"), encrypted);
        assertFalse(encrypted.contains("Camel"), encrypted);

        String decrypted = template.requestBody("direct:decryptElement", stream(encrypted), String.class);
        assertTrue(decrypted.contains("<name>Camel</name>"), decrypted);
    }

    @Test
    void secureTagContentsWithStreamBody() {
        String encrypted = template.requestBody("direct:encryptContents", stream(XML), String.class);
        assertTrue(encrypted.contains("EncryptedData"), encrypted);
        assertTrue(encrypted.contains("http://www.w3.org/2001/04/xmlenc#Content"), encrypted);
        assertFalse(encrypted.contains("Camel"), encrypted);

        String decrypted = template.requestBody("direct:decryptContents", stream(encrypted), String.class);
        assertTrue(decrypted.contains("<name>Camel</name>"), decrypted);
    }

    private static ByteArrayInputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // without stream caching, the stream body can only be read once
                from("direct:encryptElement").noStreamCaching()
                        .marshal(new XMLSecurityDataFormat("/order/name", false, passPhrase));
                from("direct:decryptElement").noStreamCaching()
                        .unmarshal(new XMLSecurityDataFormat("/order", false, passPhrase));

                from("direct:encryptContents").noStreamCaching()
                        .marshal(new XMLSecurityDataFormat("/order/name", true, passPhrase));
                from("direct:decryptContents").noStreamCaching()
                        .unmarshal(new XMLSecurityDataFormat("/order/name", true, passPhrase));
            }
        };
    }
}
