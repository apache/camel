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

import org.apache.hc.core5.http.HttpException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TextPlainEntityTest {

    @Test
    void test_parse() throws IOException, HttpException {

        String parsedMimeMessage;
        try (MimeEntity mimeEntity = EntityParser.parseEntity(MESSAGE.getBytes(StandardCharsets.US_ASCII))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            mimeEntity.writeTo(out);
            parsedMimeMessage = new String(out.toByteArray(), StandardCharsets.US_ASCII);
        }

        Assertions.assertEquals(parsedMimeMessage, MESSAGE);
    }

    String MESSAGE =
            """
            Content-Type: text/plain; charset=US-ASCII\r
            Content-Transfer-Encoding: binary\r
            \r
            <root>
            \t<item/>
            </root>
            """;
}
