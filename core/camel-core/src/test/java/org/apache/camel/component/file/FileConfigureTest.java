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
import org.apache.camel.Processor;
import org.apache.camel.ResolveEndpointFailedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileConfigureTest extends ContextTestSupport {

    private static final String EXPECT_PATH
            = "target" + File.separator + "data" + File.separator + "FileConfigureTest" + File.separator + "bar";
    private static final String EXPECT_FILE = "some" + File.separator + "nested" + File.separator + "filename.txt";

    private static final Processor DUMMY_PROCESSOR = new Processor() {
        public void process(Exchange exchange) throws Exception {
            // Do nothing here
        }
    };

    @Test
    public void testUriConfigurations() throws Exception {
        assertFileEndpoint("file://target/data/FileConfigureTest/bar", EXPECT_PATH, false);
        assertFileEndpoint("file://target/data/FileConfigureTest/bar?delete=true", EXPECT_PATH, false);
        assertFileEndpoint("file:target/data/FileConfigureTest/bar?delete=true", EXPECT_PATH, false);
        assertFileEndpoint("file:target/data/FileConfigureTest/bar", EXPECT_PATH, false);
        assertFileEndpoint("file://target/data/FileConfigureTest/bar/", EXPECT_PATH, false);
        assertFileEndpoint("file://target/data/FileConfigureTest/bar/?delete=true", EXPECT_PATH, false);
        assertFileEndpoint("file:target/data/FileConfigureTest/bar/?delete=true", EXPECT_PATH, false);
        assertFileEndpoint("file:target/data/FileConfigureTest/bar/", EXPECT_PATH, false);
        assertFileEndpoint("file:/target/data/FileConfigureTest/bar/",
                File.separator + EXPECT_PATH + File.separator + EXPECT_FILE, true);
        assertFileEndpoint("file:/", File.separator, true);
        assertFileEndpoint("file:///", File.separator, true);
    }

    @Test
    public void testUriWithParameters() {
        FileEndpoint endpoint
                = resolveMandatoryEndpoint(
                        "file:///C:/camel/temp?delay=10&useFixedDelay=true&initialDelay=10&bridgeErrorHandler=true"
                                           + "&autoCreate=false&startingDirectoryMustExist=true&directoryMustExist=true&readLock=changed",
                        FileEndpoint.class);
        assertNotNull(endpoint, "Could not find file endpoint");
        assertTrue(endpoint.isStartingDirectoryMustExist(), "Get a wrong option of StartingDirectoryMustExist");

        endpoint = resolveMandatoryEndpoint(
                "file:///C:/camel/temp?delay=10&useFixedDelay=true&initialDelay=10&startingDirectoryMustExist=true"
                                            + "&bridgeErrorHandler=true&autoCreate=false&directoryMustExist=true&readLock=changed",
                FileEndpoint.class);

        assertNotNull(endpoint, "Could not find file endpoint");
        assertTrue(endpoint.isStartingDirectoryMustExist(), "Get a wrong option of StartingDirectoryMustExist");

        endpoint = resolveMandatoryEndpoint(
                "file:///C:/camel/temp?delay=10&startingDirectoryMustExist=true&useFixedDelay=true&initialDelay=10"
                                            + "&bridgeErrorHandler=true&autoCreate=false&directoryMustExist=true&readLock=changed",
                FileEndpoint.class);

        assertNotNull(endpoint, "Could not find file endpoint");
        assertTrue(endpoint.isStartingDirectoryMustExist(), "Get a wrong option of StartingDirectoryMustExist");

        endpoint = resolveMandatoryEndpoint("file:///C:/camel/temp?delay=10&useFixedDelay=true&initialDelay=10",
                FileEndpoint.class);

        assertNotNull(endpoint, "Could not find file endpoint");
        assertFalse(endpoint.isStartingDirectoryMustExist(), "Get a wrong option of StartingDirectoryMustExist");
    }

    @Test
    public void testUriWithCharset() {
        FileEndpoint endpoint
                = resolveMandatoryEndpoint("file://target/data/FileConfigureTest/bar?charset=UTF-8", FileEndpoint.class);
        assertNotNull(endpoint, "Could not find endpoint: file://target/data/FileConfigureTest/bar?charset=UTF-8");
        assertEquals("UTF-8", endpoint.getCharset(), "Get a wrong charset");

        Exception ex = assertThrows(Exception.class,
                () -> resolveMandatoryEndpoint("file://target/data/FileConfigureTest/bar?charset=ASSI", FileEndpoint.class),
                "Expect a configure exception here");

        boolean b = ex instanceof ResolveEndpointFailedException;
        assertTrue(b, "Get the wrong exception type here");
    }

    @Test
    public void testConsumerConfigurations() throws Exception {
        FileConsumer consumer = createFileConsumer("file://target/data/FileConfigureTest/bar?recursive=true");
        assertNotNull(consumer);

        Exception ex = assertThrows(Exception.class,
                () -> createFileConsumer("file://target/data/FileConfigureTest/bar?recursiv=true"),
                "Expect a configure exception here");

        boolean b = ex instanceof ResolveEndpointFailedException;
        assertTrue(b, "Get the wrong exception type here");
    }

    private FileConsumer createFileConsumer(String endpointUri) throws Exception {
        FileEndpoint endpoint = resolveMandatoryEndpoint(endpointUri, FileEndpoint.class);
        return endpoint.createConsumer(DUMMY_PROCESSOR);
    }

    private void assertFileEndpoint(String endpointUri, String expectedPath, boolean absolute) {
        FileEndpoint endpoint = resolveMandatoryEndpoint(endpointUri, FileEndpoint.class);
        assertNotNull(endpoint, "Could not find endpoint: " + endpointUri);

        if (!absolute) {
            File file = endpoint.getFile();
            String path = file.getPath();
            assertDirectoryEquals("For uri: " + endpointUri + " the file is not equal", expectedPath, path);

            file = new File(expectedPath + (expectedPath.endsWith(File.separator) ? "" : File.separator) + EXPECT_FILE);
            GenericFile<File> consumedFile = FileConsumer.asGenericFile(endpoint.getFile().getPath(), file, null, false);

            assertEquals(EXPECT_FILE, consumedFile.getRelativeFilePath());
        }
    }
}
