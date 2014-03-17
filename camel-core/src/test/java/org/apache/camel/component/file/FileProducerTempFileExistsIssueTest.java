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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;

/**
 * @version 
 */
public class FileProducerTempFileExistsIssueTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/tempprefix");
        super.setUp();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testIllegalConfiguration() throws Exception {
        try {
            context.getEndpoint("file://target/tempprefix?fileExist=Append&tempPrefix=foo").createProducer();
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("You cannot set both fileExist=Append and tempPrefix/tempFileName options", e.getMessage());
        }

        try {
            context.getEndpoint("file://target/tempprefix?fileExist=Append&tempFileName=foo").createProducer();
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("You cannot set both fileExist=Append and tempPrefix/tempFileName options", e.getMessage());
        }
    }

    public void testWriteUsingTempPrefixButFileExist() throws Exception {
        template.sendBodyAndHeader("file://target/tempprefix", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/tempprefix?tempPrefix=foo", "Bye World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/tempprefix/hello.txt");
        assertEquals(true, file.exists());
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    public void testWriteUsingTempPrefixButBothFileExist() throws Exception {
        template.sendBodyAndHeader("file://target/tempprefix", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/tempprefix", "Hello World", Exchange.FILE_NAME, "foohello.txt");
        template.sendBodyAndHeader("file://target/tempprefix?tempPrefix=foo", "Bye World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/tempprefix/hello.txt");
        assertEquals(true, file.exists());
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    public void testWriteUsingTempPrefixButFileExistOverride() throws Exception {
        template.sendBodyAndHeader("file://target/tempprefix", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/tempprefix?tempPrefix=foo&fileExist=Override", "Bye World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/tempprefix/hello.txt");
        assertEquals(true, file.exists());
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    public void testWriteUsingTempPrefixButFileExistIgnore() throws Exception {
        template.sendBodyAndHeader("file://target/tempprefix", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/tempprefix?tempPrefix=foo&fileExist=Ignore", "Bye World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/tempprefix/hello.txt");
        assertEquals(true, file.exists());
        // should not write new file as we should ignore
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));
    }

    public void testWriteUsingTempPrefixButFileExistFail() throws Exception {
        template.sendBodyAndHeader("file://target/tempprefix", "Hello World", Exchange.FILE_NAME, "hello.txt");
        try {
            template.sendBodyAndHeader("file://target/tempprefix?tempPrefix=foo&fileExist=Fail", "Bye World", Exchange.FILE_NAME, "hello.txt");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            GenericFileOperationFailedException cause = assertIsInstanceOf(GenericFileOperationFailedException.class, e.getCause());
            assertTrue(cause.getMessage().startsWith("File already exist"));
        }

        File file = new File("target/tempprefix/hello.txt");
        assertEquals(true, file.exists());

        // should not write new file as we should fail
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));
    }

}
