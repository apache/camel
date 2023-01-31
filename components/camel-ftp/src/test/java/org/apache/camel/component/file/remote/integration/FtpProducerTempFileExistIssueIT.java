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
package org.apache.camel.component.file.remote.integration;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpProducerTempFileExistIssueIT extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/tempprefix/?password=admin";
    }

    @Test
    public void testIllegalConfiguration() {
        String uri = getFtpUrl() + "&fileExist=Append&tempPrefix=foo";
        Endpoint endpoint = context.getEndpoint(uri);

        Exception ex = assertThrows(IllegalArgumentException.class, () -> endpoint.createProducer());
        assertEquals("You cannot set both fileExist=Append and tempPrefix/tempFileName options",
                ex.getMessage());
    }

    @Test
    public void testWriteUsingTempPrefixButFileExist() throws Exception {
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        Thread.sleep(500);

        template.sendBodyAndHeader(getFtpUrl() + "&tempPrefix=foo", "Bye World", Exchange.FILE_NAME, "hello.txt");

        File file = service.ftpFile("tempprefix/hello.txt").toFile();
        await().atMost(500, TimeUnit.MILLISECONDS).untilAsserted(() -> assertTrue(file.exists()));
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testWriteUsingTempPrefixButBothFileExist() throws Exception {
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", Exchange.FILE_NAME, "foohello.txt");

        Thread.sleep(500);

        template.sendBodyAndHeader(getFtpUrl() + "&tempPrefix=foo", "Bye World", Exchange.FILE_NAME, "hello.txt");

        File file = service.ftpFile("tempprefix/hello.txt").toFile();
        await().atMost(500, TimeUnit.MILLISECONDS).untilAsserted(() -> assertTrue(file.exists()));
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testWriteUsingTempPrefixButFileExistOverride() throws Exception {
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        Thread.sleep(500);

        template.sendBodyAndHeader(getFtpUrl() + "&tempPrefix=foo&fileExist=Override", "Bye World", Exchange.FILE_NAME,
                "hello.txt");

        File file = service.ftpFile("tempprefix/hello.txt").toFile();
        await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertTrue(file.exists()));
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testWriteUsingTempPrefixButFileExistIgnore() throws Exception {
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        Thread.sleep(500);

        template.sendBodyAndHeader(getFtpUrl() + "&tempPrefix=foo&fileExist=Ignore", "Bye World", Exchange.FILE_NAME,
                "hello.txt");

        File file = service.ftpFile("tempprefix/hello.txt").toFile();
        // should not write new file as we should ignore
        await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file)));
    }

    @Test
    public void testWriteUsingTempPrefixButFileExistFail() throws Exception {
        template.sendBodyAndHeader(getFtpUrl(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        Thread.sleep(500);

        String uri = getFtpUrl() + "&tempPrefix=foo&fileExist=Fail";
        Exception ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Bye World", Exchange.FILE_NAME, "hello.txt"));

        GenericFileOperationFailedException cause
                = assertIsInstanceOf(GenericFileOperationFailedException.class, ex.getCause());
        assertTrue(cause.getMessage().startsWith("File already exist"));

        File file = service.ftpFile("tempprefix/hello.txt").toFile();
        // should not write new file as we should ignore
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file)));
    }
}
