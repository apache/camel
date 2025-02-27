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
package org.apache.camel.component.spring.batch.support;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CamelItemWriterTest extends CamelTestSupport {

    // Fixtures

    CamelItemWriter<String> camelItemWriter;

    String message = "message";

    // Camel fixtures

    @Override
    protected void doPostSetup() throws Exception {
        camelItemWriter = new CamelItemWriter<>(template(), "seda:queue");
    }

    // Tests

    @Test
    public void shouldReadMessage() throws Exception {
        // When
        camelItemWriter.write(Chunk.of(message));

        // Then
        assertEquals(message, consumer().receiveBody("seda:queue"));
    }

}
