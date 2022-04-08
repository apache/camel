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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.exec.impl.ExecCommandExecutorMock;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_ARGS;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_EXECUTABLE;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_EXIT_VALUES;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_TIMEOUT;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_WORKING_DIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the functionality of {@link ExecProducer}
 */
@CamelSpringTest
@ContextConfiguration(locations = { "exec-mock-executor-context.xml" })
public class ExecProducerTest {

    @Produce("direct:input")
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

            public void process(Exchange exchange) {
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
        final String[] args = { "-version", "classpath:c:/program files/test/" };
        producerTemplate.send(new Processor() {

            public void process(Exchange exchange) {
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

            public void process(Exchange exchange) {
                exchange.getIn().setBody("noinput");
                exchange.getIn().setHeader(EXEC_COMMAND_TIMEOUT, "1000");
            }
        });
        assertEquals(1000, execCommandExecutorMock.lastCommandResult.getCommand().getTimeout());
    }

    @Test
    @DirtiesContext
    public void testExitValues() {
        producerTemplate.send(new Processor() {

            public void process(Exchange exchange) {
                exchange.getIn().setBody("noinput");
                exchange.getIn().setHeader(EXEC_COMMAND_EXIT_VALUES, "0,1");
            }
        });
        assertTrue(execCommandExecutorMock.lastCommandResult.getCommand().getExitValues().contains(1));
    }

    @Test
    @DirtiesContext
    public void testExitValueNone() {
        producerTemplate.send(new Processor() {

            public void process(Exchange exchange) {
                exchange.getIn().setBody("noinput");
                exchange.getIn().setHeader(EXEC_COMMAND_EXIT_VALUES, "");
            }
        });
        assertEquals(0, execCommandExecutorMock.lastCommandResult.getCommand().getExitValues().size());
    }

    @Test
    @DirtiesContext
    public void testInputLines() throws IOException {
        // String must be convertible to InputStream
        final String input = "line1" + System.lineSeparator() + "line2";
        producerTemplate.send(new Processor() {

            public void process(Exchange exchange) {
                exchange.getIn().setBody(input);
            }
        });
        assertEquals(input,
                IOUtils.toString(execCommandExecutorMock.lastCommandResult.getCommand().getInput(), Charset.defaultCharset()));
    }

    @Test
    @DirtiesContext
    public void testInputLinesNotConvertibleToInputStream() {
        // String must be convertible to InputStream
        final Integer notConvertibleToInputStreamBody = Integer.valueOf(1);
        Exchange e = producerTemplate.send(new Processor() {

            public void process(Exchange exchange) {
                exchange.getIn().setBody(notConvertibleToInputStreamBody);
            }
        });
        ExecResult result = e.getIn().getBody(ExecResult.class);
        assertNotNull(result);
        assertNull(result.getCommand().getInput());
    }

    @Test
    @DirtiesContext
    public void testNullInBody() {
        // Null body must also be supported
        Exchange e = producerTemplate.send(new Processor() {

            public void process(Exchange exchange) {
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
            public void process(Exchange exchange) {
                exchange.getIn().setBody("");
                exchange.getIn().setHeader(EXEC_COMMAND_WORKING_DIR, workingDir);
            }
        });
        assertEquals(workingDir, execCommandExecutorMock.lastCommandResult.getCommand().getWorkingDir());
    }

    @Test
    @DirtiesContext
    public void testInInOnlyExchange() {
        Exchange exchange = producerTemplate.send(new Processor() {
            public void process(Exchange exchange) {
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
    public void testOutCapableExchange() {
        Exchange exchange = producerTemplate.send(new Processor() {
            public void process(Exchange exchange) {
                exchange.setPattern(ExchangePattern.InOut);
                exchange.getIn().setBody("inout");
            }
        });
        // test the conversion
        ExecResult result = exchange.getMessage().getBody(ExecResult.class);
        assertNotNull(result);
    }
}
