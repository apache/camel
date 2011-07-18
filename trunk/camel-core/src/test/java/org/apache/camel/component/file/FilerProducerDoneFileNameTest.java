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
import org.apache.camel.ExpressionIllegalSyntaxException;

/**
 * Unit test for writing done files
 */
public class FilerProducerDoneFileNameTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/done");
        super.setUp();
    }

    public void testProducerConstantDoneFileName() throws Exception {
        template.sendBodyAndHeader("file:target/done?doneFileName=done", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/done/hello.txt").getAbsoluteFile();
        assertEquals("File should exists", true, file.exists());

        File done = new File("target/done/done").getAbsoluteFile();
        assertEquals("Done file should exists", true, done.exists());
    }

    public void testProducerPrefixDoneFileName() throws Exception {
        template.sendBodyAndHeader("file:target/done?doneFileName=done-${file:name}", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/done/hello.txt").getAbsoluteFile();
        assertEquals("File should exists", true, file.exists());

        File done = new File("target/done/done-hello.txt").getAbsoluteFile();
        assertEquals("Done file should exists", true, done.exists());
    }

    public void testProducerExtDoneFileName() throws Exception {
        template.sendBodyAndHeader("file:target/done?doneFileName=${file:name}.done", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/done/hello.txt").getAbsoluteFile();
        assertEquals("File should exists", true, file.exists());

        File done = new File("target/done/hello.txt.done").getAbsoluteFile();
        assertEquals("Done file should exists", true, done.exists());
    }

    public void testProducerReplaceExtDoneFileName() throws Exception {
        template.sendBodyAndHeader("file:target/done?doneFileName=${file:name.noext}.done", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/done/hello.txt").getAbsoluteFile();
        assertEquals("File should exists", true, file.exists());

        File done = new File("target/done/hello.done").getAbsoluteFile();
        assertEquals("Done file should exists", true, done.exists());
    }

    public void testProducerInvalidDoneFileName() throws Exception {
        try {
            template.sendBodyAndHeader("file:target/done?doneFileName=${file:parent}/foo", "Hello World", Exchange.FILE_NAME, "hello.txt");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            ExpressionIllegalSyntaxException cause = assertIsInstanceOf(ExpressionIllegalSyntaxException.class, e.getCause());
            assertTrue(cause.getMessage(), cause.getMessage().endsWith("Cannot resolve reminder: ${file:parent}/foo"));
        }
    }

    public void testProducerEmptyDoneFileName() throws Exception {
        try {
            template.sendBodyAndHeader("file:target/done?doneFileName=", "Hello World", Exchange.FILE_NAME, "hello.txt");
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
