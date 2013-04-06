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
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.junit.Test;

/**
 * @version 
 */
public class FtpProducerDoneFileNameTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/done?password=admin";
    }

    @Test
    public void testProducerConstantDoneFileName() throws Exception {
        template.sendBodyAndHeader(getFtpUrl() + "&doneFileName=done", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File(FTP_ROOT_DIR + "/done/hello.txt");
        assertEquals("File should exists", true, file.exists());

        File done = new File(FTP_ROOT_DIR + "/done/done");
        assertEquals("Done file should exists", true, done.exists());
    }

    @Test
    public void testProducerPrefixDoneFileName() throws Exception {
        template.sendBodyAndHeader(getFtpUrl() + "&doneFileName=done-${file:name}", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File(FTP_ROOT_DIR + "/done/hello.txt");
        assertEquals("File should exists", true, file.exists());

        File done = new File(FTP_ROOT_DIR + "/done/done-hello.txt");
        assertEquals("Done file should exists", true, done.exists());
    }

    @Test
    public void testProducerExtDoneFileName() throws Exception {
        template.sendBodyAndHeader(getFtpUrl() + "&doneFileName=${file:name}.done", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File(FTP_ROOT_DIR + "/done/hello.txt");
        assertEquals("File should exists", true, file.exists());

        File done = new File(FTP_ROOT_DIR + "/done/hello.txt.done");
        assertEquals("Done file should exists", true, done.exists());
    }

    @Test
    public void testProducerReplaceExtDoneFileName() throws Exception {
        template.sendBodyAndHeader(getFtpUrl() + "&doneFileName=${file:name.noext}.done", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File(FTP_ROOT_DIR + "/done/hello.txt");
        assertEquals("File should exists", true, file.exists());

        File done = new File(FTP_ROOT_DIR + "/done/hello.done");
        assertEquals("Done file should exists", true, done.exists());
    }

    @Test
    public void testProducerInvalidDoneFileName() throws Exception {
        try {
            template.sendBodyAndHeader(getFtpUrl() + "&doneFileName=${file:parent}/foo", "Hello World", Exchange.FILE_NAME, "hello.txt");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            ExpressionIllegalSyntaxException cause = assertIsInstanceOf(ExpressionIllegalSyntaxException.class, e.getCause());
            assertTrue(cause.getMessage(), cause.getMessage().endsWith("Cannot resolve reminder: ${file:parent}/foo"));
        }
    }

    @Test
    public void testProducerEmptyDoneFileName() throws Exception {
        try {
            template.sendBodyAndHeader(getFtpUrl() + "&doneFileName=", "Hello World", Exchange.FILE_NAME, "hello.txt");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertTrue(cause.getMessage(), cause.getMessage().startsWith("doneFileName must be specified and not empty"));
        }
    }
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

}
