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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.junit.Before;
import org.junit.Test;

public class FileProducerTempFileExistsIssueTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/tempprefix");
        super.setUp();
    }

    @Test
    public void testIllegalConfiguration() throws Exception {
        try {
            context.getEndpoint("file://target/data/tempprefix?fileExist=Append&tempPrefix=foo").createProducer();
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("You cannot set both fileExist=Append and tempPrefix/tempFileName options", e.getMessage());
        }

        try {
            context.getEndpoint("file://target/data/tempprefix?fileExist=Append&tempFileName=foo").createProducer();
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("You cannot set both fileExist=Append and tempPrefix/tempFileName options", e.getMessage());
        }
    }

    @Test
    public void testWriteUsingTempPrefixButFileExist() throws Exception {
        template.sendBodyAndHeader("file://target/data/tempprefix", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/data/tempprefix?tempPrefix=foo", "Bye World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/data/tempprefix/hello.txt");
        assertEquals(true, file.exists());
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testWriteUsingTempPrefixButBothFileExist() throws Exception {
        template.sendBodyAndHeader("file://target/data/tempprefix", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/data/tempprefix", "Hello World", Exchange.FILE_NAME, "foohello.txt");
        template.sendBodyAndHeader("file://target/data/tempprefix?tempPrefix=foo", "Bye World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/data/tempprefix/hello.txt");
        assertEquals(true, file.exists());
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testWriteUsingTempPrefixButFileExistOverride() throws Exception {
        template.sendBodyAndHeader("file://target/data/tempprefix", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/data/tempprefix?tempPrefix=foo&fileExist=Override", "Bye World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/data/tempprefix/hello.txt");
        assertEquals(true, file.exists());
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testWriteUsingTempPrefixButFileExistIgnore() throws Exception {
        template.sendBodyAndHeader("file://target/data/tempprefix", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/data/tempprefix?tempPrefix=foo&fileExist=Ignore", "Bye World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/data/tempprefix/hello.txt");
        assertEquals(true, file.exists());
        // should not write new file as we should ignore
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testWriteUsingTempPrefixButFileExistFail() throws Exception {
        template.sendBodyAndHeader("file://target/data/tempprefix", "Hello World", Exchange.FILE_NAME, "hello.txt");
        try {
            template.sendBodyAndHeader("file://target/data/tempprefix?tempPrefix=foo&fileExist=Fail", "Bye World", Exchange.FILE_NAME, "hello.txt");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            GenericFileOperationFailedException cause = assertIsInstanceOf(GenericFileOperationFailedException.class, e.getCause());
            assertTrue(cause.getMessage().startsWith("File already exist"));
        }

        File file = new File("target/data/tempprefix/hello.txt");
        assertEquals(true, file.exists());

        // should not write new file as we should fail
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));
    }

}
