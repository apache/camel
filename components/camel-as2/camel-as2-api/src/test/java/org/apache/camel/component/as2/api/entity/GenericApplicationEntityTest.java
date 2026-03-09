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
package org.apache.camel.component.as2.api.entity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;

import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2TransferEncoding;
import org.apache.camel.component.as2.api.util.EntityUtils;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class GenericApplicationEntityTest {

    private static final String EDI_MESSAGE = "UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'";

    @BeforeAll
    static void setUp() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void genericApplicationEntityPreservesContent() throws Exception {
        byte[] content = EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII);
        ContentType contentType = ContentType.create("text/plain", StandardCharsets.US_ASCII);
        GenericApplicationEntity entity = new GenericApplicationEntity(content, contentType, null, true, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        assertArrayEquals(content, out.toByteArray());
    }

    @Test
    void genericApplicationEntityGetEdiMessage() {
        byte[] content = EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII);
        ContentType contentType = ContentType.create("text/plain", StandardCharsets.US_ASCII);
        GenericApplicationEntity entity = new GenericApplicationEntity(content, contentType, null, true, null);

        Object ediMessage = entity.getEdiMessage();
        assertInstanceOf(String.class, ediMessage);
        assertEquals(EDI_MESSAGE, ediMessage);
    }

    @ParameterizedTest
    @ValueSource(strings = { "text/plain", "application/octet-stream" })
    void extractEdiPayloadAcceptsNonStandardContentTypes(String mimeType) throws Exception {
        byte[] content = EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII);
        ContentType contentType = ContentType.create(mimeType, StandardCharsets.US_ASCII);

        BasicClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.addHeader(AS2Header.CONTENT_TYPE, contentType.toString());
        InputStream is = new ByteArrayInputStream(content);
        request.setEntity(new BasicHttpEntity(is, content.length, contentType));

        ApplicationEntity ediEntity = HttpMessageUtils.extractEdiPayload(request,
                new HttpMessageUtils.DecrpytingAndSigningInfo(null, null));

        assertNotNull(ediEntity, "EDI entity should not be null for content type: " + mimeType);
        assertInstanceOf(GenericApplicationEntity.class, ediEntity);
        assertEquals(EDI_MESSAGE, ediEntity.getEdiMessage().toString());
    }

    @Test
    void createEDIEntityReturnsGenericForUnknownType() throws Exception {
        byte[] content = EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII);
        ContentType contentType = ContentType.create("text/plain", StandardCharsets.US_ASCII);

        ApplicationEntity entity = EntityUtils.createEDIEntity(content, contentType,
                AS2TransferEncoding.NONE, false, "test.txt");

        assertInstanceOf(GenericApplicationEntity.class, entity);
    }
}
