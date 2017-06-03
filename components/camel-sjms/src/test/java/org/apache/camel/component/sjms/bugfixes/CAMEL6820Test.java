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
package org.apache.camel.component.sjms.bugfixes;

import java.io.File;
import java.io.InputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

/**
 * Unit test for CAMEL_6820Test.  This test is to verify the ability to 
 * support the Camel File Component more cleanly along with better support 
 * for ByteMessages. 
 */
public class CAMEL6820Test extends JmsTestSupport {

    private static final String TEST_DATA_DIR = "target/testdata";
    private static final String FILE_OUTPUT_URI = "file:" + TEST_DATA_DIR;
    private static final String FILE_INPUT_URI = "file:" + TEST_DATA_DIR;
    private static final String SJMS_QUEUE_URI = "sjms:queue:file.converter.queue";
    private static final String MOCK_RESULT_URI = "mock:result";

    @Test
    public void testCamelGenericFileConverterMessage() throws Exception {
        File f = new File(TEST_DATA_DIR);
        
        // First make sure the directories are empty or purged so we don't get bad data on a 
        // test that is run against an uncleaned target directory
        if (f.exists()) {
            FileUtils.deleteDirectory(new File(TEST_DATA_DIR));
        
        }
        
        // Then add the directory back
        f.mkdirs();
        
        // Make sure the SjmsComponent is available
        SjmsComponent component = context.getComponent("sjms", SjmsComponent.class);
        assertNotNull(component);

        // Create the test String
        final String expectedBody = "Hello World";
        
        // Create the Mock endpoint
        MockEndpoint mock = getMockEndpoint(MOCK_RESULT_URI);
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(expectedBody);

        // Send the message to a file to be read by the file component
        template.sendBody(FILE_OUTPUT_URI, expectedBody);

        // Verify that it is working correctly
        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(FILE_INPUT_URI)
                    .convertBodyTo(InputStream.class)
                    .to(SJMS_QUEUE_URI);

                from(SJMS_QUEUE_URI)
                    .convertBodyTo(String.class)
                    .to(MOCK_RESULT_URI);
            }
        };
    }
}
