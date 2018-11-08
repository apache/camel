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
package org.apache.camel.issues;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.junit.Before;
import org.junit.Test;

/**
 * @version 
 */
public class FilePollingConsumerIssueTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/fpc");
        super.setUp();
    }

    @Test
    public void testFilePollingConsumer() throws Exception {
        template.sendBodyAndHeader("file://target/fpc", "Hello World", Exchange.FILE_NAME, "hello.txt");

        Endpoint endpoint = context.getEndpoint("file://target/fpc?initialDelay=0&delay=10&fileName=hello.txt");
        PollingConsumer consumer = endpoint.createPollingConsumer();
        consumer.start();
        Exchange exchange = consumer.receive(5000);
        assertNotNull(exchange);

        assertEquals("hello.txt", exchange.getIn().getHeader(Exchange.FILE_NAME, String.class));
        assertEquals("Hello World", exchange.getIn().getBody(String.class));

        consumer.stop();
    }
}
