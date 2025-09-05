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
package org.apache.camel.component.sjms.converter;

import java.io.File;
import java.io.InputStream;

import jakarta.jms.Message;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsMessage;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

public class SjmsConverterTest extends JmsTestSupport {

    private static final String TEST_DATA_DIR = "target/SjmsConverterTest";
    private static final String FILE_OUTPUT_URI = "file:" + TEST_DATA_DIR;
    private static final String FILE_INPUT_URI = "file:" + TEST_DATA_DIR;
    private static final String SJMS_QUEUE_URI = "sjms:queue:file.converter.queue.JmsMessageTest";
    private static final String MOCK_RESULT_URI = "mock:result";

    @Test
    public void toJakartaJmsMessage() throws Exception {
        File f = new File(TEST_DATA_DIR);

        // First make sure the directories are empty or purged so we don't get bad data on a
        // test that is run against an uncleaned target directory
        if (f.exists()) {
            FileUtils.deleteDirectory(new File(TEST_DATA_DIR));
        }

        // Then add the directory back
        f.mkdirs();

        // Create the test String
        final String expectedBody = "Hello World";

        // Create the Mock endpoint
        MockEndpoint mock = getMockEndpoint(MOCK_RESULT_URI);
        mock.expectedMessageCount(1);
        mock.allMessages().inMessage().isInstanceOf(SjmsMessage.class);
        mock.allMessages().body(Message.class).isInstanceOf(Message.class);

        // Send the message to a file to be read by the file component
        template.sendBody(FILE_OUTPUT_URI, expectedBody);

        // Verify that it is working correctly
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(FILE_INPUT_URI)
                        .convertBodyTo(InputStream.class)
                        .to(SJMS_QUEUE_URI);

                from(SJMS_QUEUE_URI)
                        .to(MOCK_RESULT_URI);
            }
        };
    }
}
