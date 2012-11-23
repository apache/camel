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
package org.apache.camel.component.file.remote;

import java.io.File;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.Test;

/**
 * @version 
 */
public class FtpProducerTempFileExistIssueTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/tempprefix/?password=admin";
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testIllegalConfiguration() throws Exception {
        try {
            context.getEndpoint(getFtpUrl() + "&fileExist=Append&tempPrefix=foo").createProducer();
        } catch (IllegalArgumentException e) {
            assertEquals("You cannot set both fileExist=Append and tempPrefix options", e.getMessage());
        }
    }

    @Test
    public void testWriteUsingTempPrefixButFileExist() throws Exception {
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        Thread.sleep(500);

        template.sendBodyAndHeader(getFtpUrl() + "&tempPrefix=foo", "Bye World", Exchange.FILE_NAME, "hello.txt");

        Thread.sleep(500);

        File file = new File(FTP_ROOT_DIR + "/tempprefix/hello.txt");
        assertEquals(true, file.exists());
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testWriteUsingTempPrefixButBothFileExist() throws Exception {
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", Exchange.FILE_NAME, "foohello.txt");

        Thread.sleep(500);

        template.sendBodyAndHeader(getFtpUrl() + "&tempPrefix=foo", "Bye World", Exchange.FILE_NAME, "hello.txt");

        Thread.sleep(500);

        File file = new File(FTP_ROOT_DIR + "/tempprefix/hello.txt");
        assertEquals(true, file.exists());
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testWriteUsingTempPrefixButFileExistOverride() throws Exception {
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        Thread.sleep(500);

        template.sendBodyAndHeader(getFtpUrl() + "&tempPrefix=foo&fileExist=Override", "Bye World", Exchange.FILE_NAME, "hello.txt");

        Thread.sleep(500);

        File file = new File(FTP_ROOT_DIR + "/tempprefix/hello.txt");
        assertEquals(true, file.exists());
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testWriteUsingTempPrefixButFileExistIgnore() throws Exception {
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        Thread.sleep(500);

        template.sendBodyAndHeader(getFtpUrl() + "&tempPrefix=foo&fileExist=Ignore", "Bye World", Exchange.FILE_NAME, "hello.txt");

        Thread.sleep(500);

        File file = new File(FTP_ROOT_DIR + "/tempprefix/hello.txt");
        // should not write new file as we should ignore
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testWriteUsingTempPrefixButFileExistFail() throws Exception {
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        Thread.sleep(500);

        try {
            template.sendBodyAndHeader(getFtpUrl() + "&tempPrefix=foo&fileExist=Fail", "Bye World", Exchange.FILE_NAME, "hello.txt");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            GenericFileOperationFailedException cause = assertIsInstanceOf(GenericFileOperationFailedException.class, e.getCause());
            assertTrue(cause.getMessage().startsWith("File already exist"));
        }

        Thread.sleep(500);

        File file = new File(FTP_ROOT_DIR + "/tempprefix/hello.txt");
        // should not write new file as we should ignore
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));
    }
}