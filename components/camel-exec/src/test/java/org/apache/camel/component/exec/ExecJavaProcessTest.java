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

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.exec.impl.ProvokeExceptionExecCommandExecutor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.reifier.RouteReifier;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_ARGS;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_EXECUTABLE;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_TIMEOUT;
import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_WORKING_DIR;
import static org.apache.camel.component.exec.ExecBinding.EXEC_EXIT_VALUE;
import static org.apache.camel.component.exec.ExecBinding.EXEC_STDERR;
import static org.apache.camel.component.exec.ExecBinding.EXEC_USE_STDERR_ON_EMPTY_STDOUT;
import static org.apache.camel.component.exec.ExecEndpoint.NO_TIMEOUT;
import static org.apache.camel.component.exec.ExecTestUtils.buildJavaExecutablePath;
import static org.apache.camel.component.exec.ExecutableJavaProgram.EXIT_WITH_VALUE_0;
import static org.apache.camel.component.exec.ExecutableJavaProgram.EXIT_WITH_VALUE_1;
import static org.apache.camel.component.exec.ExecutableJavaProgram.PRINT_ARGS_STDOUT;
import static org.apache.camel.component.exec.ExecutableJavaProgram.PRINT_IN_STDERR;
import static org.apache.camel.component.exec.ExecutableJavaProgram.PRINT_IN_STDOUT;
import static org.apache.camel.component.exec.ExecutableJavaProgram.READ_INPUT_LINES_AND_PRINT_THEM;
import static org.apache.camel.component.exec.ExecutableJavaProgram.SLEEP_WITH_TIMEOUT;
import static org.apache.camel.component.exec.ExecutableJavaProgram.THREADS;
import static org.apache.commons.io.IOUtils.LINE_SEPARATOR;

/**
 * Tests the functionality of the {@link ExecComponent}, executing<br>
 * <i>java org.apache.camel.component.exec.ExecutableJavaProgram</i> <br>
 * command. <b>Note, that the tests assume, that the JAVA_HOME system variable
 * is set.</b> This is a more credible assumption, than assuming that java is in
 * the path, because the Maven scripts build the path to java with the JAVA_HOME
 * environment variable.
 *
 * @see {@link ExecutableJavaProgram}
 */
public class ExecJavaProcessTest extends CamelTestSupport {

    private static final String EXECUTABLE_PROGRAM_ARG = ExecutableJavaProgram.class.getName();
    
    @Produce("direct:input")
    ProducerTemplate producerTemplate;

    @EndpointInject("mock:output")
    MockEndpoint output;
    
    @BindToRegistry("executorMock")
    private ProvokeExceptionExecCommandExecutor provokerMock = new ProvokeExceptionExecCommandExecutor();

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Test
    public void testExecJavaProcessExitCode0() throws Exception {
        context.start();

        output.setExpectedMessageCount(1);
        output.expectedHeaderReceived(EXEC_EXIT_VALUE, 0);

        sendExchange(EXIT_WITH_VALUE_0, NO_TIMEOUT);
        output.assertIsSatisfied();
    }

    @Test
    public void testExecJavaProcessExitCode1() throws Exception {
        context.start();

        output.setExpectedMessageCount(1);
        output.expectedHeaderReceived(EXEC_EXIT_VALUE, 1);

        sendExchange(EXIT_WITH_VALUE_1, NO_TIMEOUT);
        output.assertIsSatisfied();
    }

    @Test
    public void testExecJavaProcessStdout() throws Exception {
        context.start();

        String commandArgument = PRINT_IN_STDOUT;
        output.setExpectedMessageCount(1);
        output.expectedHeaderReceived(EXEC_EXIT_VALUE, 0);

        Exchange e = sendExchange(commandArgument, NO_TIMEOUT);
        ExecResult inBody = e.getIn().getBody(ExecResult.class);

        output.assertIsSatisfied();
        assertEquals(PRINT_IN_STDOUT, IOUtils.toString(inBody.getStdout(), Charset.defaultCharset()));
    }

    @Test
    public void testConvertResultToString() throws Exception {
        context.start();

        String commandArgument = PRINT_IN_STDOUT;
        output.setExpectedMessageCount(1);

        Exchange e = sendExchange(commandArgument, NO_TIMEOUT);
        output.assertIsSatisfied();
        String out = e.getIn().getBody(String.class);
        assertEquals(PRINT_IN_STDOUT, out);
    }

    @Test
    public void testByteArrayInputStreamIsResetInConverter() throws Exception {
        context.start();

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
        context.start();

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
        context.start();

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
        context.start();

        String commandArgument = PRINT_IN_STDOUT;
        output.setExpectedMessageCount(1);

        Exchange e = sendExchange(commandArgument, NO_TIMEOUT);
        output.assertIsSatisfied();
        InputStream out = e.getIn().getBody(InputStream.class);
        assertEquals(PRINT_IN_STDOUT, IOUtils.toString(out, Charset.defaultCharset()));
    }

    @Test
    public void testConvertResultToByteArray() throws Exception {
        context.start();

        String commandArgument = PRINT_IN_STDOUT;
        output.setExpectedMessageCount(1);

        Exchange e = sendExchange(commandArgument, NO_TIMEOUT);
        output.assertIsSatisfied();
        byte[] out = e.getIn().getBody(byte[].class);
        assertNotNull(out);
        assertEquals(PRINT_IN_STDOUT, new String(out, Charset.defaultCharset()));
    }

    @Test
    public void testInvalidWorkingDir() throws Exception {
        context.start();

        String commandArgument = PRINT_IN_STDOUT;
        final List<String> args = buildArgs(commandArgument);
        final String javaAbsolutePath = buildJavaExecutablePath();

        Exchange e = producerTemplate.send(new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EXEC_COMMAND_EXECUTABLE, javaAbsolutePath);
                exchange.getIn().setHeader(EXEC_COMMAND_WORKING_DIR, "\\cdd:///invalidWWorkginDir");
                exchange.getIn().setHeader(EXEC_COMMAND_ARGS, args);
            }
        });
        assertEquals(ExecException.class, e.getException().getClass());
    }

    /**
     * Test print in stdout from threads.
     */
    @Test
    public void testExecJavaProcessThreads() throws Exception {
        context.start();

        output.setExpectedMessageCount(1);
        Exchange exchange = sendExchange(THREADS, NO_TIMEOUT);

        String err = IOUtils.toString(exchange.getIn().getHeader(EXEC_STDERR, InputStream.class), Charset.defaultCharset());
        ExecResult result = exchange.getIn().getBody(ExecResult.class);
        String[] outs = IOUtils.toString(result.getStdout(), Charset.defaultCharset()).split(LINE_SEPARATOR);
        String[] errs = err.split(LINE_SEPARATOR);

        output.assertIsSatisfied();
        assertEquals(ExecutableJavaProgram.LINES_TO_PRINT_FROM_EACH_THREAD, outs.length);
        assertEquals(ExecutableJavaProgram.LINES_TO_PRINT_FROM_EACH_THREAD, errs.length);
    }

    /**
     * Test print in stdout using string as args
     */
    @Test
    public void testExecJavaArgsAsString() throws Exception {
        context.start();

        output.setExpectedMessageCount(1);

        Exchange exchange = producerTemplate.send("direct:input", new Processor() {
            public void process(Exchange exchange) throws Exception {
                final String javaAbsolutePath = buildJavaExecutablePath();

                // use string for args
                String classpath = System.getProperty("java.class.path");
                String args = "-cp \"" + classpath + "\" " + EXECUTABLE_PROGRAM_ARG + " " + PRINT_IN_STDOUT;

                exchange.getIn().setBody("hello");
                exchange.getIn().setHeader(EXEC_COMMAND_EXECUTABLE, javaAbsolutePath);
                exchange.getIn().setHeader(EXEC_COMMAND_ARGS, args);
                exchange.getIn().setHeader(EXEC_USE_STDERR_ON_EMPTY_STDOUT, true);
            }
        });

        output.assertIsSatisfied();

        ExecResult result = exchange.getIn().getBody(ExecResult.class);
        assertNotNull(result);

        String out = IOConverter.toString(result.getStdout(), exchange);
        assertEquals(PRINT_IN_STDOUT, out);
    }

    /**
     * Test print in stdout using string as args with quotes
     */
    @Test
    public void testExecJavaArgsAsStringWithQuote() throws Exception {
        context.start();

        output.setExpectedMessageCount(1);

        Exchange exchange = producerTemplate.send("direct:input", new Processor() {
            public void process(Exchange exchange) throws Exception {
                final String javaAbsolutePath = buildJavaExecutablePath();

                // use string for args
                String classpath = System.getProperty("java.class.path");
                String args = "-cp \"" + classpath + "\" " + EXECUTABLE_PROGRAM_ARG + " " + PRINT_ARGS_STDOUT + " \"Hello World\"";

                exchange.getIn().setBody("hello");
                exchange.getIn().setHeader(EXEC_COMMAND_EXECUTABLE, javaAbsolutePath);
                exchange.getIn().setHeader(EXEC_COMMAND_ARGS, args);
                exchange.getIn().setHeader(EXEC_USE_STDERR_ON_EMPTY_STDOUT, true);
            }
        });

        output.assertIsSatisfied();

        ExecResult result = exchange.getIn().getBody(ExecResult.class);
        assertNotNull(result);

        String out = IOConverter.toString(result.getStdout(), exchange);
        assertTrue(out, out.contains("1Hello World"));
    }

    /**
     * Test print in stdout using string as args with quotes
     */
    @Test
    public void testExecJavaArgsAsStringWithoutQuote() throws Exception {
        context.start();

        output.setExpectedMessageCount(1);

        Exchange exchange = producerTemplate.send("direct:input", new Processor() {
            public void process(Exchange exchange) throws Exception {
                final String javaAbsolutePath = buildJavaExecutablePath();

                // use string for args
                String classpath = System.getProperty("java.class.path");
                String args = "-cp \"" + classpath + "\" " + EXECUTABLE_PROGRAM_ARG + " " + PRINT_ARGS_STDOUT + " Hello World";

                exchange.getIn().setBody("hello");
                exchange.getIn().setHeader(EXEC_COMMAND_EXECUTABLE, javaAbsolutePath);
                exchange.getIn().setHeader(EXEC_COMMAND_ARGS, args);
                exchange.getIn().setHeader(EXEC_USE_STDERR_ON_EMPTY_STDOUT, true);
            }
        });

        output.assertIsSatisfied();

        ExecResult result = exchange.getIn().getBody(ExecResult.class);
        assertNotNull(result);

        String out = IOConverter.toString(result.getStdout(), exchange);
        assertTrue(out, out.contains("1Hello"));
        assertTrue(out, out.contains("2World"));
    }

    /**
     * Test if the process will be terminate in about a second
     */
    @Test
    public void testExecJavaProcessTimeout() throws Exception {
        context.start();

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
        context.start();

        final StringBuilder builder = new StringBuilder();
        int lines = 10;
        for (int t = 1; t < lines; t++) {
            builder.append("Line" + t + LINE_SEPARATOR);
        }
        String whiteSpaceSeparatedLines = builder.toString();
        String expected = builder.toString();

        Exchange e = sendExchange(READ_INPUT_LINES_AND_PRINT_THEM, 20000, whiteSpaceSeparatedLines, false);
        ExecResult inBody = e.getIn().getBody(ExecResult.class);
        assertEquals(expected, IOUtils.toString(inBody.getStdout(), Charset.defaultCharset()));
    }

    /**
     * Test for thrown {@link ExecException} and access stderr and exitValue
     * of thrown Exception
     */
    @Test
    public void testExecJavaProcessWithThrownExecException() throws Exception {
        RouteReifier.adviceWith(context.getRouteDefinitions().get(0), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveByToString(".*java.*").replace().to("exec:java?commandExecutor=#executorMock");
            }
        });
        context.start();

        output.setExpectedMessageCount(0);

        Exchange out = sendFailExchange(EXIT_WITH_VALUE_0, NO_TIMEOUT);

        //test if exitValue and stderr are accessible through thrown ExecException
        ExecException ee = (ExecException) out.getException();
        assertTrue(ee.getExitValue() > 0);
        assertNotNull(ee.getStderr());

        output.assertIsSatisfied();
    }

    protected Exchange sendExchange(final Object commandArgument, final long timeout) {
        return sendExchange(commandArgument, buildArgs(commandArgument), timeout, "testBody", false);
    }

    protected Exchange sendFailExchange(final Object commandArgument, final long timeout) {
        return sendExchange(commandArgument, buildFailArgs(commandArgument), timeout, "testBody", false);
    }

    protected Exchange sendExchange(final Object commandArgument, final long timeout, final String body, final boolean useStderrOnEmptyStdout) {
        return sendExchange(commandArgument, buildArgs(commandArgument), timeout, body, useStderrOnEmptyStdout);
    }

    protected Exchange sendExchange(final Object commandArgument, final List<String> args, final long timeout, final String body, final boolean useStderrOnEmptyStdout) {
        final String javaAbsolutePath = buildJavaExecutablePath();

        return producerTemplate.send(new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(body);
                exchange.getIn().setHeader(EXEC_COMMAND_EXECUTABLE, javaAbsolutePath);
                exchange.getIn().setHeader(EXEC_COMMAND_TIMEOUT, timeout);
                exchange.getIn().setHeader(EXEC_COMMAND_ARGS, args);
                if (useStderrOnEmptyStdout) {
                    exchange.getIn().setHeader(EXEC_USE_STDERR_ON_EMPTY_STDOUT, true);
                }
            }
        });
    }

    List<String> buildArgs(Object commandArgument) {
        String classpath = System.getProperty("java.class.path");
        List<String> args = new ArrayList<>();
        args.add("-cp");
        args.add(classpath);
        args.add(EXECUTABLE_PROGRAM_ARG);
        args.add(commandArgument.toString());
        return args;
    }

    /**
     * Build arguments for execution which will result in error
     */
    List<String> buildFailArgs(Object commandArgument) {
        String classpath = System.getProperty("java.class.path");
        List<String> args = new ArrayList<>();
        args.add("-failArg");
        args.add(classpath);
        args.add(EXECUTABLE_PROGRAM_ARG);
        args.add(commandArgument.toString());
        return args;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:input").to("exec:java").to("mock:output");
            }
        };
    }

}
