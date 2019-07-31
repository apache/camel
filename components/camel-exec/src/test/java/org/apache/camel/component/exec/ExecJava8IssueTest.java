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
package org.apache.camel.component.exec;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.exec.OS;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Test to duplicate issues with Camel's exec command in Java 8 on Unix
 * This issue appears to be caused by a race condition, so this test does not always fail
 */
public class ExecJava8IssueTest extends Assert {

    private File tempDir;
    private final String tempDirName = name();
    private final String tempFileName = name();

    @Before
    public void setUp() {
        tempDir = new File("target", tempDirName);
        if (!(tempDir.mkdir())) {
            fail("Couldn't create temp dir for test");
        }
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(tempDir);
    }

    @Test
    public void test() throws Exception {

        if (!OS.isFamilyUnix()) {
            System.err.println("The test 'CamelExecTest' does not support the following OS : " + System.getProperty("os.name"));
            return;
        }

        String tempFilePath = tempDir.getAbsolutePath() + "/" + tempFileName;

        final File script = File.createTempFile("script", ".sh", tempDir);

        writeScript(script);

        final String exec = "bash?args=" + script.getAbsolutePath() + " " + tempFilePath + "&outFile=" + tempFilePath;

        DefaultCamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:source")
                        .to("file:" + tempDir.getAbsolutePath() + "?fileName=" + tempFileName)
                        .to("exec:" + exec)
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                String output = exchange.getIn().getBody(String.class);
                                assertEquals("hello world\n", output);
                            }
                        });

            }
        });

        context.start();

        ProducerTemplate pt = context.createProducerTemplate();
        String payload = "hello";

        pt.sendBody("direct:source", payload);
    }

    /**
     * Creates a script which will append " world" to a file
     */
    private void writeScript(File script) throws IOException {
        try (FileWriter fw = new FileWriter(script); 
            PrintWriter pw = new PrintWriter(fw);) {
            String s = "echo \" world\" >> $1";
            pw.print(s);
        } 
    }

    /**
     * Returns a random UUID
     */
    private String name() {
        return UUID.randomUUID().toString();
    }
}