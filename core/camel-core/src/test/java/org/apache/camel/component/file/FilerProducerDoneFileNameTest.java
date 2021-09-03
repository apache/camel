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

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for writing done files
 */
public class FilerProducerDoneFileNameTest extends ContextTestSupport {

    private Properties myProp = new Properties();

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myProp", myProp);
        return jndi;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("ref:myProp");
        return context;
    }

    @Test
    public void testProducerConstantDoneFileName() throws Exception {
        template.sendBodyAndHeader(fileUri("?doneFileName=done"), "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertFileExists(testFile("hello.txt"));
        assertFileExists(testFile("done"));
    }

    @Test
    public void testProducerPrefixDoneFileName() throws Exception {
        template.sendBodyAndHeader(fileUri("?doneFileName=done-${file:name}"), "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        assertFileExists(testFile("hello.txt"));
        assertFileExists(testFile("done-hello.txt"));
    }

    @Test
    public void testProducerExtDoneFileName() throws Exception {
        template.sendBodyAndHeader(fileUri("?doneFileName=${file:name}.done"), "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        assertFileExists(testFile("hello.txt"));
        assertFileExists(testFile("hello.txt.done"));
    }

    @Test
    public void testProducerReplaceExtDoneFileName() throws Exception {
        template.sendBodyAndHeader(fileUri("?doneFileName=${file:name.noext}.done"), "Hello World",
                Exchange.FILE_NAME, "hello.txt");

        assertFileExists(testFile("hello.txt"));
        assertFileExists(testFile("hello.done"));
    }

    @Test
    public void testProducerInvalidDoneFileName() throws Exception {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(fileUri("?doneFileName=${file:parent}/foo"), "Hello World", Exchange.FILE_NAME,
                        "hello.txt"));
        ExpressionIllegalSyntaxException cause = assertIsInstanceOf(ExpressionIllegalSyntaxException.class, e.getCause());
        assertTrue(cause.getMessage().endsWith("Cannot resolve reminder: ${file:parent}/foo"), cause.getMessage());
    }

    @Test
    public void testProducerEmptyDoneFileName() throws Exception {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(fileUri("?doneFileName="), "Hello World", Exchange.FILE_NAME, "hello.txt"));
        IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().startsWith("doneFileName must be specified and not empty"), cause.getMessage());
    }

    @Test
    public void testProducerPlaceholderPrefixDoneFileName() throws Exception {
        myProp.put("myDir", testDirectory().toString());

        template.sendBodyAndHeader("file:{{myDir}}?doneFileName=done-${file:name}", "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        assertFileExists(testFile("hello.txt"));
        assertFileExists(testFile("done-hello.txt"));
    }

}
