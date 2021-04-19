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
package org.apache.camel.component.vertx.kafka;

import java.util.stream.StreamSupport;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class VertxKafkaTestUtils {

    private VertxKafkaTestUtils() {
    }

    public static byte[] getHeaderValue(String headerKey, Headers headers) {
        Header foundHeader = StreamSupport.stream(headers.spliterator(), false).filter(header -> header.key().equals(headerKey))
                .findFirst().orElse(null);
        assertNotNull(foundHeader, "Header should be sent");
        return foundHeader.value();
    }

}
