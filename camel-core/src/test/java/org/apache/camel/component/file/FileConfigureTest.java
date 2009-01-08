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
package org.apache.camel.component.file;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.impl.DefaultMessage;

/**
 * @version $Revision$
 */
public class FileConfigureTest extends ContextTestSupport {
    private static final String EXPECT_PATH = "target" + File.separator + "foo" + File.separator + "bar";
    private static final String EXPECT_FILE = "some" + File.separator + "nested" + File.separator + "filename.txt";
    private static final Processor DUMMY_PROCESSOR = new Processor() {
        public void process(Exchange exchange) throws Exception {
            // Do nothing here
        }
    };
    public void testUriConfigurations() throws Exception {
        assertFileEndpoint("file://target/foo/bar", EXPECT_PATH);
        assertFileEndpoint("file://target/foo/bar?delete=true", EXPECT_PATH);
        assertFileEndpoint("file:target/foo/bar?delete=true", EXPECT_PATH);
        assertFileEndpoint("file:target/foo/bar", EXPECT_PATH);
        assertFileEndpoint("file://target/foo/bar/", EXPECT_PATH);
        assertFileEndpoint("file://target/foo/bar/?delete=true", EXPECT_PATH);
        assertFileEndpoint("file:target/foo/bar/?delete=true", EXPECT_PATH);
        assertFileEndpoint("file:target/foo/bar/", EXPECT_PATH);
        assertFileEndpoint("file:/target/foo/bar/", File.separator + EXPECT_PATH);
        assertFileEndpoint("file:/", File.separator);
        assertFileEndpoint("file:///", File.separator);
    }

    public void testConsumerConfigurations() throws Exception {
        FileConsumer consumer = createFileConsumer("file://target/foo/bar?consumer.recursive=true");
        assertEquals("The recurisive should be true", consumer.isRecursive(), true);
        try {
            consumer = createFileConsumer("file://target/foo/bar?consumer.recursiv=true");
            fail("Expect a configure exception here");
        } catch (Exception ex) {
            assertTrue("Get the wrong exception type here", ex instanceof ResolveEndpointFailedException);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        // one of the above tests created a /target folder in the root we want to get rid of when testing
        deleteDirectory("/target");
    }

    private FileConsumer createFileConsumer(String endpointUri) throws Exception {
        FileEndpoint endpoint = resolveMandatoryEndpoint(endpointUri, FileEndpoint.class);
        return (FileConsumer)endpoint.createConsumer(DUMMY_PROCESSOR);
    }

    private void assertFileEndpoint(String endpointUri, String expectedPath) {
        FileEndpoint endpoint = resolveMandatoryEndpoint(endpointUri, FileEndpoint.class);
        assertNotNull("Could not find endpoint: " + endpointUri, endpoint);

        File file = endpoint.getFile();
        String path = file.getPath();
        assertEquals("For uri: " + endpointUri + " the file is not equal", expectedPath, path);

        File consumedFile = new File(expectedPath + (expectedPath.endsWith(File.separator) ? "" : File.separator) + EXPECT_FILE);
        Message message = new DefaultMessage();
        endpoint.configureMessage(consumedFile, message);
        assertEquals(EXPECT_FILE, message.getHeader(FileComponent.HEADER_FILE_NAME));
    }
}
