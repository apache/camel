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
package org.apache.camel.component.exec.impl;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.exec.ExecResult;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.exec.ExecTestUtils.buildJavaExecutablePath;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tests are Disabledd by default, because they are OS-specific. On demand they can be run manually to validate the
 * documentation examples for that OS.
 */

public class ExecDocumentationExamplesTest extends CamelTestSupport {

    private static final String ANT_BUILD_FILE_NAME = "CamelExecTestAntBuildFile.xml";

    private static final String ANT_OUT_FILE_NAME = "CamelExecOutFile.txt";

    private static final String ANT_BUILD_FILE_CONTENT = buildAntFileContent();

    private static final String TEST_MSG = "Hello Camel Exec!";

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecDocumentationExamplesTest.class);

    @Produce("direct:javaVersion")
    protected ProducerTemplate templateJavaVersion;

    @Produce("direct:javaVersionWorkingDir")
    protected ProducerTemplate templateJavaVersionWorkingDir;

    @Produce("direct:execAnt")
    protected ProducerTemplate templateExecAnt;

    @Produce("direct:execAntWithOutFile")
    protected ProducerTemplate templateExecAntWithOutFile;

    @Produce("direct:wordCount")
    protected ProducerTemplate templateWordCount;

    @Test
    @Disabled
    public void testExecLinuxWordCount() throws Exception {
        // use type conversion here
        ExecResult body = templateWordCount.requestBody((Object) "test", ExecResult.class);
        assertNotNull(body);

    }

    /**
     * The test assumes, that java is in the system path
     */
    @Test
    @Disabled
    public void testJavaVersion() throws Exception {
        ExecResult body = templateJavaVersion.requestBody((Object) "test", ExecResult.class);
        InputStream out = body.getStdout();
        InputStream err = body.getStderr();
        // Strange that Sun Java 1.5 writes the -version in the syserr
        assertNull(out);
        assertNotNull(err);
        String outString = IOUtils.toString(err, Charset.defaultCharset());
        LOGGER.info("Received stdout: " + outString);
        assertTrue(outString.contains("java version"));
    }

    @Test
    @Disabled
    public void testWinJavaVersionWorkingDir() throws Exception {
        ExecResult body = templateJavaVersionWorkingDir.requestBody((Object) "test", ExecResult.class);
        InputStream out = body.getStdout();
        InputStream err = body.getStderr();
        // Strange that Sun Java 1.5 writes the -version in the syserr
        assertNull(out);
        assertNotNull(err);
        String outerr = IOUtils.toString(err, Charset.defaultCharset());
        LOGGER.info("Received stderr: " + outerr);
        assertTrue(outerr.contains("java version"));
    }

    /**
     * The test assumes that Apache ant is installed
     */
    @Test
    @Disabled
    public void testExecWinAnt() throws Exception {
        File f = new File(ANT_BUILD_FILE_NAME);
        f.createNewFile();
        FileUtils.writeStringToFile(f, ANT_BUILD_FILE_CONTENT, Charset.defaultCharset());
        assertTrue(f.exists(), "You must create a sample build file!");
        ExecResult body = templateExecAnt.requestBody((Object) "test", ExecResult.class);
        String stdout = IOUtils.toString(body.getStdout(), Charset.defaultCharset());
        assertNull(body.getStderr());
        assertTrue(stdout.contains(TEST_MSG), "The ant script should print" + TEST_MSG);
        f.delete();
    }

    /**
     * The test assumes that Apache ant is installed
     */
    @Test
    @Disabled
    public void testExecWinAntWithOutFile() throws Exception {
        File f = new File(ANT_BUILD_FILE_NAME);
        f.createNewFile();
        FileUtils.writeStringToFile(f, ANT_BUILD_FILE_CONTENT, Charset.defaultCharset());
        assertTrue(f.exists(), "You must create a sample build file!");
        // use type conversion here
        InputStream body = templateExecAntWithOutFile.requestBody((Object) "test", InputStream.class);
        String bodyString = IOUtils.toString(body, Charset.defaultCharset());
        assertTrue(bodyString.contains(TEST_MSG), "The ant script should print" + TEST_MSG);
        f.delete();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // word count
                from("direct:wordCount").to("exec:wc?args=--words /usr/share/dict/words").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // By default, the body is ExecResult instance
                        assertIsInstanceOf(ExecResult.class, exchange.getIn().getBody());
                        // Use the Camel Exec String type converter to
                        // convert the ExecResult to String
                        // In this case, the stdout is considered as output
                        String wordCountOutput = exchange.getIn().getBody(String.class);
                        // do something with the output
                        log.info(wordCountOutput);
                    }

                });

                // example 1 in the component docs
                from("direct:javaVersion").to("exec:java?args=-version -server");
                // example 2 in the component docs
                from("direct:javaVersionWorkingDir")
                        .to("exec:" + buildJavaExecutablePath() + "?args=-version -Duser.name=Camel&workingDir=C:/temp");

                // advanced, test ant
                from("direct:execAnt").to("exec:ant.bat?args=-f " + ANT_BUILD_FILE_NAME);

                // advanced, test ant with out file
                from("direct:execAntWithOutFile")
                        .to("exec:ant.bat?args=-f " + ANT_BUILD_FILE_NAME + " -l " + ANT_OUT_FILE_NAME + "&outFile="
                            + ANT_OUT_FILE_NAME)
                        .process(new Processor() {

                            public void process(Exchange exchange) throws Exception {
                                InputStream outFile = exchange.getIn().getBody(InputStream.class);
                                // do something with the out file here
                                log.info(IOUtils.toString(outFile, Charset.defaultCharset()));
                            }

                        });
            }
        };
    }

    private static String buildAntFileContent() {
        StringBuilder builder = new StringBuilder();
        builder.append("<project name=\"TestExec\" default=\"test\" basedir=\".\">");
        builder.append("<target name=\"test\">");
        builder.append("<echo message=\"" + TEST_MSG + "\"/>");
        builder.append("</target>");
        builder.append("</project>");
        return builder.toString();
    }

}
