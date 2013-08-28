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
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.JndiRegistry;

/**
 * Unit test for writing done files
 */
public class FilerProducerDoneFileNameTest extends ContextTestSupport {

    private Properties myProp = new Properties();

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/done");
        super.setUp();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myProp", myProp);
        return jndi;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setLocation("ref:myProp");

        return context;
    }

    public void testProducerConstantDoneFileName() throws Exception {
        template.sendBodyAndHeader("file:target/done?doneFileName=done", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/done/hello.txt");
        assertEquals("File should exists", true, file.exists());

        File done = new File("target/done/done");
        assertEquals("Done file should exists", true, done.exists());
    }

    public void testProducerPrefixDoneFileName() throws Exception {
        template.sendBodyAndHeader("file:target/done?doneFileName=done-${file:name}", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/done/hello.txt");
        assertEquals("File should exists", true, file.exists());

        File done = new File("target/done/done-hello.txt");
        assertEquals("Done file should exists", true, done.exists());
    }

    public void testProducerExtDoneFileName() throws Exception {
        template.sendBodyAndHeader("file:target/done?doneFileName=${file:name}.done", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/done/hello.txt");
        assertEquals("File should exists", true, file.exists());

        File done = new File("target/done/hello.txt.done");
        assertEquals("Done file should exists", true, done.exists());
    }

    public void testProducerReplaceExtDoneFileName() throws Exception {
        template.sendBodyAndHeader("file:target/done?doneFileName=${file:name.noext}.done", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/done/hello.txt");
        assertEquals("File should exists", true, file.exists());

        File done = new File("target/done/hello.done");
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

    public void testProducerPlaceholderPrefixDoneFileName() throws Exception {
        myProp.put("myDir", "target/done");

        template.sendBodyAndHeader("file:{{myDir}}?doneFileName=done-${file:name}", "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File("target/done/hello.txt");
        assertEquals("File should exists", true, file.exists());

        File done = new File("target/done/done-hello.txt");
        assertEquals("Done file should exists", true, done.exists());
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
