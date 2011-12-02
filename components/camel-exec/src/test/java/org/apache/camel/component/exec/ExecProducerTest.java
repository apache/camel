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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.exec.impl.ExecCommandExecutorMock;
import org.apache.commons.io.IOUtils;

import org.junit.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_ARGS;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_EXECUTABLE;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_TIMEOUT;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_WORKING_DIR;
import static org.apache.commons.io.IOUtils.LINE_SEPARATOR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Test the functionality of {@link ExecProducer}
 */
@ContextConfiguration(locations = {"exec-mock-executor-context.xml"})
public class ExecProducerTest extends AbstractJUnit4SpringContextTests {

    @Produce(uri = "direct:input")
    private ProducerTemplate producerTemplate;

    @Autowired
    private ExecCommandExecutorMock execCommandExecutorMock;

    @Test
    @DirtiesContext
    public void testWithContextConfiguration() {
        producerTemplate.sendBody("direct:input", "test");
        // the expected string is defined in the route configuration
        assertEquals("mockedByCommandExecutorMock.exe", execCommandExecutorMock.lastCommandResult.getCommand().getExecutable());
    }

    @Test
    @DirtiesContext
    public void testOverrideExecutable() {
        final String command = "java";

        producerTemplate.send(new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("noinput");
                exchange.getIn().setHeader(EXEC_COMMAND_EXECUTABLE, command);
            }
        });

        assertEquals(command, execCommandExecutorMock.lastCommandResult.getCommand().getExecutable());
    }

    /**
     * Tests that the args are set literally.
     */
    @Test
    @DirtiesContext
    public void testOverrideArgs() {
        final String[] args = {"-version", "classpath:c:/program files/test/"};
        producerTemplate.send(new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("noinput");
                exchange.getIn().setHeader(EXEC_COMMAND_ARGS, Arrays.asList(args));
            }
        });
        List<String> commandArgs = execCommandExecutorMock.lastCommandResult.getCommand().getArgs();

        assertEquals(args[0], commandArgs.get(0));
        assertEquals(args[1], commandArgs.get(1));
    }

    @Test
    @DirtiesContext
    public void testOverrideTimeout() {
        producerTemplate.send(new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("noinput");
                exchange.getIn().setHeader(EXEC_COMMAND_TIMEOUT, "1000");
            }
        });
        assertEquals(1000, execCommandExecutorMock.lastCommandResult.getCommand().getTimeout());
    }

    @Test
    @DirtiesContext
    public void testInputLines() throws IOException {
        // String must be convertible to InputStream
        final String input = "line1" + LINE_SEPARATOR + "line2";
        producerTemplate.send(new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(input);
            }
        });
        assertEquals(input, IOUtils.toString(execCommandExecutorMock.lastCommandResult.getCommand().getInput()));
    }

    @Test
    @DirtiesContext
    public void testInputLinesNotConvertibleToInputStream() throws IOException {
        // String must be convertible to InputStream
        final Integer notConvertibleToInputStreamBody = new Integer(1);
        Exchange e = producerTemplate.send(new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(notConvertibleToInputStreamBody);
            }
        });
        ExecResult result = e.getIn().getBody(ExecResult.class);
        assertNotNull(result);
        assertNull(result.getCommand().getInput());
    }
    
    @Test
    @DirtiesContext
    public void testNullInBody() throws IOException {
        // Null body must also be supported
        Exchange e = producerTemplate.send(new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(null);
            }
        });
        ExecResult result = e.getIn().getBody(ExecResult.class);
        assertNotNull(result);
        assertNull(result.getCommand().getInput());
    }

    @Test
    @DirtiesContext
    public void testOverrideWorkingDir() {
        final String workingDir = "c:/program files/test";

        producerTemplate.send(new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("");
                exchange.getIn().setHeader(EXEC_COMMAND_WORKING_DIR, workingDir);
            }
        });
        assertEquals(workingDir, execCommandExecutorMock.lastCommandResult.getCommand().getWorkingDir());
    }

    @Test
    @DirtiesContext
    public void testInInOnlyExchange() throws Exception {
        Exchange exchange = producerTemplate.send(new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOnly);
                exchange.getIn().setBody("inonly");
            }
        });
        // test the conversion
        ExecResult result = exchange.getIn().getBody(ExecResult.class);
        assertNotNull(result);
    }

    @Test
    @DirtiesContext
    public void testOutCapableExchange() throws Exception {
        Exchange exchange = producerTemplate.send(new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                exchange.getIn().setBody("inout");
            }
        });
        // test the conversion
        ExecResult result = exchange.getOut().getBody(ExecResult.class);
        assertNotNull(result);
    }
}
