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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileProducerTempFileExistsIssueTest extends ContextTestSupport {

    @Test
    public void testIllegalConfigurationPrefix() throws Exception {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> context.getEndpoint(fileUri("?fileExist=Append&tempPrefix=foo")).createProducer());
        assertEquals("You cannot set both fileExist=Append and tempPrefix/tempFileName options", e.getMessage());
    }

    @Test
    public void testIllegalConfigurationFileName() throws Exception {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> context.getEndpoint(fileUri("?fileExist=Append&tempFileName=foo")).createProducer());
        assertEquals("You cannot set both fileExist=Append and tempPrefix/tempFileName options", e.getMessage());
    }

    @Test
    public void testWriteUsingTempPrefixButFileExist() throws Exception {
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(fileUri("?tempPrefix=foo"), "Bye World", Exchange.FILE_NAME, "hello.txt");

        assertFileExists(testFile("hello.txt"), "Bye World");
    }

    @Test
    public void testWriteUsingTempPrefixButBothFileExist() throws Exception {
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "foohello.txt");
        template.sendBodyAndHeader(fileUri("?tempPrefix=foo"), "Bye World", Exchange.FILE_NAME, "hello.txt");

        assertFileExists(testFile("hello.txt"), "Bye World");
    }

    @Test
    public void testWriteUsingTempPrefixButFileExistOverride() throws Exception {
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(fileUri("?tempPrefix=foo&fileExist=Override"), "Bye World", Exchange.FILE_NAME, "hello.txt");

        assertFileExists(testFile("hello.txt"), "Bye World");
    }

    @Test
    public void testWriteUsingTempPrefixButFileExistIgnore() throws Exception {
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(fileUri("?tempPrefix=foo&fileExist=Ignore"), "Bye World", Exchange.FILE_NAME, "hello.txt");

        assertFileExists(testFile("hello.txt"), "Hello World");
    }

    @Test
    public void testWriteUsingTempPrefixButFileExistFail() throws Exception {
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");
        CamelExecutionException e = assertThrows(CamelExecutionException.class, () -> template
                .sendBodyAndHeader(fileUri("?tempPrefix=foo&fileExist=Fail"), "Bye World", Exchange.FILE_NAME, "hello.txt"));
        GenericFileOperationFailedException cause = assertIsInstanceOf(GenericFileOperationFailedException.class, e.getCause());
        assertTrue(cause.getMessage().startsWith("File already exist"));

        assertFileExists(testFile("hello.txt"), "Hello World");
    }

}
