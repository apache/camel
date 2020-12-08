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
package org.apache.camel.component.file.remote;

import java.io.File;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FtpProducerDoneFileNameTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/done?password=admin";
    }

    @Test
    public void testProducerConstantDoneFileName() throws Exception {
        template.sendBodyAndHeader(getFtpUrl() + "&doneFileName=done", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File(service.getFtpRootDir() + "/done/hello.txt");
        assertEquals(true, file.exists(), "File should exists");

        File done = new File(service.getFtpRootDir() + "/done/done");
        assertEquals(true, done.exists(), "Done file should exists");
    }

    @Test
    public void testProducerPrefixDoneFileName() throws Exception {
        template.sendBodyAndHeader(getFtpUrl() + "&doneFileName=done-${file:name}", "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        File file = new File(service.getFtpRootDir() + "/done/hello.txt");
        assertEquals(true, file.exists(), "File should exists");

        File done = new File(service.getFtpRootDir() + "/done/done-hello.txt");
        assertEquals(true, done.exists(), "Done file should exists");
    }

    @Test
    public void testProducerExtDoneFileName() throws Exception {
        template.sendBodyAndHeader(getFtpUrl() + "&doneFileName=${file:name}.done", "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        File file = new File(service.getFtpRootDir() + "/done/hello.txt");
        assertEquals(true, file.exists(), "File should exists");

        File done = new File(service.getFtpRootDir() + "/done/hello.txt.done");
        assertEquals(true, done.exists(), "Done file should exists");
    }

    @Test
    public void testProducerReplaceExtDoneFileName() throws Exception {
        template.sendBodyAndHeader(getFtpUrl() + "&doneFileName=${file:name.noext}.done", "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        File file = new File(service.getFtpRootDir() + "/done/hello.txt");
        assertEquals(true, file.exists(), "File should exists");

        File done = new File(service.getFtpRootDir() + "/done/hello.done");
        assertEquals(true, done.exists(), "Done file should exists");
    }

    @Test
    public void testProducerInvalidDoneFileName() throws Exception {
        String uri = getFtpUrl() + "&doneFileName=${file:parent}/foo";

        Exception ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt"));

        ExpressionIllegalSyntaxException cause = assertIsInstanceOf(ExpressionIllegalSyntaxException.class,
                ex.getCause());

        assertTrue(cause.getMessage().endsWith("Cannot resolve reminder: ${file:parent}/foo"), cause.getMessage());
    }

    @Test
    public void testProducerEmptyDoneFileName() throws Exception {
        String uri = getFtpUrl() + "&doneFileName=";
        Exception ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt"));

        IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertTrue(cause.getMessage().startsWith("doneFileName must be specified and not empty"), cause.getMessage());
    }

}
