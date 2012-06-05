/**
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

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CamelItemReaderTest extends CamelTestSupport {

    // Fixtures

    CamelItemReader<String> camelItemReader;

    String message = "message";

    // Camel fixtures

    @Override
    protected void doPostSetup() throws Exception {
        camelItemReader = new CamelItemReader<String>(consumer(), "seda:start");
        sendBody("seda:start", message);
    }

    // Tests

    @Test
    public void shouldReadMessage() throws Exception {
        // When
        String messageRead = camelItemReader.read();

        // Then
        assertEquals(message, messageRead);
    }

}
