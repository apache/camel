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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ApplicationEntityTest {

    @Test
    void checkWriteToWithMultiByteCharacter() throws IOException {
        byte[] messageBytes = "Test message with special char ó".getBytes(StandardCharsets.UTF_8);

        ContentType contentType = ContentType.create("text/plain", StandardCharsets.UTF_8);
        ApplicationEntity applicationEntity = new ApplicationEntity(messageBytes, contentType, "binary", true, null) {
            @Override
            public void close() throws IOException {
            }
        };
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        applicationEntity.writeTo(outputStream);

        byte[] actualBytes = outputStream.toByteArray();

        assertArrayEquals(messageBytes, actualBytes, "The output bytes should match the expected UTF-8 encoded bytes.");
        assertEquals(messageBytes.length, actualBytes.length,
                "The byte length should match the length of the UTF-8 encoded message.");
    }
}
