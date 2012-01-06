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
package org.apache.camel.component.exec;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.exec.OS;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_ARGS;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_EXECUTABLE;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_TIMEOUT;
import static org.apache.camel.component.exec.ExecBinding.EXEC_STDERR;
import static org.apache.camel.component.exec.ExecEndpoint.NO_TIMEOUT;
import static org.apache.camel.component.exec.ExecTestUtils.getClasspathResourceFileOrNull;
import static org.apache.camel.component.exec.ExecutableJavaProgram.PRINT_IN_STDOUT;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test executing a OS script. Use only manually, see the TODO
 */
@ContextConfiguration
public class ExecScriptTest extends AbstractJUnit4SpringContextTests {

    @Produce(uri = "direct:input")
    private ProducerTemplate producerTemplate;

    /**
     * TODO <b>the test is ignored for now to prevent accidental build
     * failures.</b> Java 1.5 does not offer a method to check if a file is
     * executable there is only a canRead method, which is not enough to
     * guarantee that the script can be executed. <br>
     * 
     * @throws Exception
     */
    @Test
    @DirtiesContext
    @Ignore
    public void testExecuteScript() throws Exception {
        File scriptFile = getExecScriptFileOrNull("exec-test-script");
        if (scriptFile != null) {
            String classpathArg = getClasspathArg();
            Exchange exchange = executeScript(scriptFile, NO_TIMEOUT, classpathArg, PRINT_IN_STDOUT);
            if (exchange != null) {
                String out = exchange.getIn().getBody(String.class);
                String err = (String)exchange.getIn().getHeader(EXEC_STDERR);

                assertNotNull(out);
                assertTrue(out.contains(PRINT_IN_STDOUT));
                assertNull(err);
            }
        } else {
            String os = System.getProperty("os.name");
            logger.warn("Executing batch scripts is not tested on " + os);
        }
    }

    private Exchange executeScript(final File scriptFile, long timeout, String... args) {
        StringBuilder argsBuilder = new StringBuilder();
        for (String arg : args) {
            argsBuilder.append(arg + " ");
        }
        final String whiteSpaceSeparatedArgs = argsBuilder.toString().trim();

        return producerTemplate.send(new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(PRINT_IN_STDOUT);
                exchange.getIn().setHeader(EXEC_COMMAND_TIMEOUT, NO_TIMEOUT);
                exchange.getIn().setHeader(EXEC_COMMAND_EXECUTABLE, scriptFile.getAbsolutePath());
                exchange.getIn().setHeader(EXEC_COMMAND_ARGS, whiteSpaceSeparatedArgs);
            }
        });
    }

    private String getClasspathArg() {
        String classpath = System.getProperty("java.class.path");
        if (OS.isFamilyWindows()) {
            // On windows the ";" character is replaced by a space by the
            // command interpreter. Thus the classpath is split with the
            // ;-token. Therefore the classpath should be quoted with double
            // quotes
            classpath = "\"\"" + classpath + "\"\"";
        } else {
            // quote only once
            classpath = "\"" + classpath + "\"";
        }
        return classpath;

    }

    private File getExecScriptFileOrNull(String scriptNameBase) {
        String resource = null;
        if (OS.isFamilyWindows()) {
            resource = scriptNameBase + ".bat";
        } else if (OS.isFamilyUnix()) {
            resource = scriptNameBase + ".sh";
        }
        File resourceFile = getClasspathResourceFileOrNull(resource);
        // TODO use canExecute here (available since java 1.6)
        if (resourceFile != null && !resourceFile.canRead()) {
            logger.warn("The resource  " + resourceFile.getAbsolutePath() + " is not readable!");
            // it is not readable, do not try to execute it
            return null;
        }
        return resourceFile;
    }
}
