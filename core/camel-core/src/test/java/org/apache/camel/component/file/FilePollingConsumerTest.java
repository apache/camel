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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.junit.Before;
import org.junit.Test;

/**
 * To test that using polling consumer with file will not keep scheduled file
 * consumer keep running in the background. It should suspend/resume the
 * consumer on demand instead.
 */
public class FilePollingConsumerTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/enrich");
        super.setUp();
    }

    @Test
    public void testPollingConsumer() throws Exception {
        template.sendBodyAndHeader("file:target/data/enrich", "Hello World", Exchange.FILE_NAME, "hello.txt");

        PollingConsumer consumer = context.getEndpoint("file:target/data/enrich").createPollingConsumer();
        consumer.start();
        Exchange exchange = consumer.receive(5000);
        assertNotNull(exchange);
        assertEquals("Hello World", exchange.getIn().getBody(String.class));

        // sleep a bit to ensure polling consumer would be suspended after we
        // have used it
        Thread.sleep(500);

        // drop a new file which should not be picked up by the consumer
        template.sendBodyAndHeader("file:target/data/enrich", "Bye World", Exchange.FILE_NAME, "bye.txt");

        // sleep a bit to ensure polling consumer would not have picked up that
        // file
        Thread.sleep(1000);

        File file = new File("target/data/enrich/bye.txt");
        assertTrue("File should exist " + file, file.exists());

        consumer.stop();
    }

}
