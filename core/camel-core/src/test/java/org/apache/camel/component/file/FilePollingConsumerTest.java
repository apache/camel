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
package org.apache.camel.component.file;

import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * To test that using polling consumer with file will not keep scheduled file consumer keep running in the background.
 * It should suspend/resume the consumer on demand instead.
 */
public class FilePollingConsumerTest extends ContextTestSupport {

    @Test
    public void testPollingConsumer() throws Exception {
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        PollingConsumer consumer = context.getEndpoint(fileUri()).createPollingConsumer();
        consumer.start();
        Exchange exchange = consumer.receive(5000);
        assertNotNull(exchange);
        assertEquals("Hello World", exchange.getIn().getBody(String.class));

        // sleep a bit to ensure polling consumer would be suspended after we
        // have used it
        Awaitility.await().pollDelay(500, TimeUnit.MILLISECONDS).untilAsserted(() -> Assertions
                .assertDoesNotThrow(() -> template.sendBodyAndHeader(fileUri(), "Bye World", Exchange.FILE_NAME, "bye.txt")));

        // sleep a bit to ensure polling consumer would not have picked up that
        // file
        Awaitility.await().pollDelay(1000, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertTrue(Files.exists(testFile("bye.txt")), "File should exist bye.txt"));

        consumer.stop();
    }

}
