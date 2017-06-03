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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_ARGS;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_EXECUTABLE;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_TIMEOUT;
import static org.apache.camel.component.exec.ExecBinding.EXEC_EXIT_VALUE;
import static org.apache.camel.component.exec.ExecBinding.EXEC_STDERR;
import static org.apache.camel.component.exec.ExecBinding.EXEC_USE_STDERR_ON_EMPTY_STDOUT;
import static org.apache.camel.component.exec.ExecEndpoint.NO_TIMEOUT;
import static org.apache.camel.component.exec.ExecTestUtils.buildJavaExecutablePath;
import static org.apache.camel.component.exec.ExecutableJavaProgram.EXIT_WITH_VALUE_0;
import static org.apache.camel.component.exec.ExecutableJavaProgram.EXIT_WITH_VALUE_1;
import static org.apache.camel.component.exec.ExecutableJavaProgram.PRINT_IN_STDERR;
import static org.apache.camel.component.exec.ExecutableJavaProgram.PRINT_IN_STDOUT;
import static org.apache.camel.component.exec.ExecutableJavaProgram.READ_INPUT_LINES_AND_PRINT_THEM;
import static org.apache.camel.component.exec.ExecutableJavaProgram.SLEEP_WITH_TIMEOUT;
import static org.apache.camel.component.exec.ExecutableJavaProgram.THREADS;
import static org.apache.commons.io.IOUtils.LINE_SEPARATOR;

/**
 * Tests the functionality of the {@link org.apache.camel.component.exec.ExecComponent}, executing<br>
 * <i>java org.apache.camel.component.exec.ExecutableJavaProgram</i> <br>
 * command. <b>Note, that the tests assume, that the JAVA_HOME system variable
 * is set.</b> This is a more credible assumption, than assuming that java is in
 * the path, because the Maven scripts build the path to java with the JAVA_HOME
 * environment variable.
 *
 * @see {@link org.apache.camel.component.exec.ExecutableJavaProgram}
 */
public class ExecJavaProcessRecipientListTest extends CamelTestSupport {

    private static final String EXECUTABLE_PROGRAM_ARG = ExecutableJavaProgram.class.getName();

    @Produce(uri = "direct:input")
    private ProducerTemplate producerTemplate;

    @EndpointInject(uri = "mock:output")
    private MockEndpoint output;

    @Test
    public void testExecJavaProcessExitCode0() throws Exception {
        output.setExpectedMessageCount(1);
        output.expectedHeaderReceived(EXEC_EXIT_VALUE, 0);

        sendExchange(EXIT_WITH_VALUE_0, NO_TIMEOUT);
        output.assertIsSatisfied();
    }

    @Test
    public void testExecJavaProcessExitCode1Direct() throws Exception {
        Exchange out = sendExchange("direct:direct", EXIT_WITH_VALUE_1, NO_TIMEOUT);

        assertNotNull(out);
        assertEquals(1, out.getIn().getHeader(EXEC_EXIT_VALUE));
    }

    @Test
    public void testExecJavaProcessExitCode1() throws Exception {
        output.setExpectedMessageCount(1);
        output.expectedHeaderReceived(EXEC_EXIT_VALUE, 1);

        sendExchange(EXIT_WITH_VALUE_1, NO_TIMEOUT);
        output.assertIsSatisfied();
    }

    @Test
    public void testExecJavaProcessStdout() throws Exception {
        String commandArgument = PRINT_IN_STDOUT;
        output.setExpectedMessageCount(1);
        output.expectedHeaderReceived(EXEC_EXIT_VALUE, 0);

        Exchange e = sendExchange(commandArgument, NO_TIMEOUT);
        ExecResult inBody = e.getIn().getBody(ExecResult.class);

        output.assertIsSatisfied();
        assertEquals(PRINT_IN_STDOUT, IOUtils.toString(inBody.getStdout()));
    }

    @Test
    public void testConvertResultToString() throws Exception {
        String commandArgument = PRINT_IN_STDOUT;
        output.setExpectedMessageCount(1);

        Exchange e = sendExchange(commandArgument, NO_TIMEOUT);
        output.assertIsSatisfied();
        String out = e.getIn().getBody(String.class);
        assertEquals(PRINT_IN_STDOUT, out);
    }

    @Test
    public void testByteArrayInputStreamIsResetInConverter() throws Exception {
        String commandArgument = PRINT_IN_STDOUT;
        output.setExpectedMessageCount(1);

        Exchange e = sendExchange(commandArgument, NO_TIMEOUT);
        String out1 = e.getIn().getBody(String.class);
        // the second conversion should not need a reset, this is handled
        // in the type converter.
        String out2 = e.getIn().getBody(String.class);

        output.assertIsSatisfied();
        assertEquals(PRINT_IN_STDOUT, out1);
        assertEquals(out1, out2);
    }

    @Test
    public void testIfStdoutIsNullStderrIsReturnedInConverter() throws Exception {
        // this will be printed
        String commandArgument = PRINT_IN_STDERR;
        output.setExpectedMessageCount(1);

        Exchange e = sendExchange(commandArgument, NO_TIMEOUT, null, true);
        ExecResult body = e.getIn().getBody(ExecResult.class);

        output.assertIsSatisfied();
        assertNull("the test executable must not print anything in stdout", body.getStdout());
        assertNotNull("the test executable must print in stderr", body.getStderr());
        // the converter must fall back to the stderr, because stdout is null
        String stderr = e.getIn().getBody(String.class);
        assertEquals(PRINT_IN_STDERR, stderr);
    }

    @Test
    public void testStdoutIsNull() throws Exception {
        // this will be printed
        String commandArgument = PRINT_IN_STDERR;
        output.setExpectedMessageCount(1);

        Exchange e = sendExchange(commandArgument, NO_TIMEOUT, null, false);
        ExecResult body = e.getIn().getBody(ExecResult.class);

        output.assertIsSatisfied();
        assertNull("the test executable must not print anything in stdout", body.getStdout());
        assertNotNull("the test executable must print in stderr", body.getStderr());
        // the converter must fall back to the stderr, because stdout is null
        String out = e.getIn().getBody(String.class);
        assertEquals("Should be empty", "", out);
    }

    @Test
    public void testConvertResultToInputStream() throws Exception {
        String commandArgument = PRINT_IN_STDOUT;
        output.setExpectedMessageCount(1);

        Exchange e = sendExchange(commandArgument, NO_TIMEOUT);
        output.assertIsSatisfied();
        InputStream out = e.getIn().getBody(InputStream.class);
        assertEquals(PRINT_IN_STDOUT, IOUtils.toString(out));
    }

    @Test
    public void testConvertResultToByteArray() throws Exception {
        String commandArgument = PRINT_IN_STDOUT;
        output.setExpectedMessageCount(1);

        Exchange e = sendExchange(commandArgument, NO_TIMEOUT);
        output.assertIsSatisfied();
        byte[] out = e.getIn().getBody(byte[].class);
        assertNotNull(out);
        assertEquals(PRINT_IN_STDOUT, new String(out));
    }

    /**
     * Test print in stdout from threads.
     */
    @Test
    public void testExecJavaProcessThreads() throws Exception {
        output.setExpectedMessageCount(1);
        Exchange exchange = sendExchange(THREADS, NO_TIMEOUT);

        String err = IOUtils.toString(exchange.getIn().getHeader(EXEC_STDERR, InputStream.class));
        ExecResult result = exchange.getIn().getBody(ExecResult.class);
        String[] outs = IOUtils.toString(result.getStdout()).split(LINE_SEPARATOR);
        String[] errs = err.split(LINE_SEPARATOR);

        output.assertIsSatisfied();
        assertEquals(ExecutableJavaProgram.LINES_TO_PRINT_FROM_EACH_THREAD, outs.length);
        assertEquals(ExecutableJavaProgram.LINES_TO_PRINT_FROM_EACH_THREAD, errs.length);
    }

    /**
     * Test if the process will be terminate in about a second
     */
    @Test
    public void testExecJavaProcessTimeout() throws Exception {
        int killAfterMillis = 1000;
        output.setExpectedMessageCount(1);
        // add some tolerance
        output.setResultMinimumWaitTime(800);
        // max (the test program sleeps 60 000)
        output.setResultWaitTime(30000);

        sendExchange(SLEEP_WITH_TIMEOUT, killAfterMillis);
        output.assertIsSatisfied();
    }

    /**
     * Test reading of input lines from the executable's stdin
     */
    @Test
    public void testExecJavaProcessInputLines() throws Exception {
        final StringBuilder builder = new StringBuilder();
        int lines = 10;
        for (int t = 1; t < lines; t++) {
            builder.append("Line" + t + LINE_SEPARATOR);
        }
        String whiteSpaceSeparatedLines = builder.toString();
        String expected = builder.toString();

        Exchange e = sendExchange(READ_INPUT_LINES_AND_PRINT_THEM, 20000, whiteSpaceSeparatedLines, false);
        ExecResult inBody = e.getIn().getBody(ExecResult.class);
        assertEquals(expected, IOUtils.toString(inBody.getStdout()));
    }

    protected Exchange sendExchange(final String endpoint, final Object commandArgument, final long timeout) {
        return sendExchange(endpoint, commandArgument, timeout, "testBody", false);
    }

    protected Exchange sendExchange(final Object commandArgument, final long timeout) {
        return sendExchange(commandArgument, timeout, "testBody", false);
    }

    protected Exchange sendExchange(final Object commandArgument, final long timeout, final String body, final boolean useStderrOnEmptyStdout) {
        return sendExchange("direct:input", commandArgument, timeout, body, useStderrOnEmptyStdout);
    }

    protected Exchange sendExchange(final String endpoint, final Object commandArgument, final long timeout, final String body, final boolean useStderrOnEmptyStdout) {
        final List<String> args = buildArgs(commandArgument);
        final String javaAbsolutePath = buildJavaExecutablePath();

        return producerTemplate.send(endpoint, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(body);
                exchange.getIn().setHeader(EXEC_COMMAND_EXECUTABLE, javaAbsolutePath);
                exchange.getIn().setHeader(EXEC_COMMAND_TIMEOUT, timeout);
                exchange.getIn().setHeader(EXEC_COMMAND_ARGS, args);
                exchange.getIn().setHeader("whereTo", "exec:java");
                if (useStderrOnEmptyStdout) {
                    exchange.getIn().setHeader(EXEC_USE_STDERR_ON_EMPTY_STDOUT, true);
                }
            }
        });
    }

    List<String> buildArgs(Object commandArgument) {
        String classpath = System.getProperty("java.class.path");
        List<String> args = new ArrayList<String>();
        args.add("-cp");
        args.add(classpath);
        args.add(EXECUTABLE_PROGRAM_ARG);
        args.add(commandArgument.toString());
        return args;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:input")
                    .recipientList(header("whereTo")).to("mock:output");

                from("direct:direct")
                    .recipientList(header("whereTo"));
            }
        };
    }

}
