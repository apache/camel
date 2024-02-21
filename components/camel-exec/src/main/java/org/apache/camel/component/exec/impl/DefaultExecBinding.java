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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.component.exec.ExecBinding;
import org.apache.camel.component.exec.ExecCommand;
import org.apache.camel.component.exec.ExecEndpoint;
import org.apache.camel.component.exec.ExecResult;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.exec.impl.ExecParseUtils.splitCommaSeparatedToListOfInts;
import static org.apache.camel.component.exec.impl.ExecParseUtils.splitToWhiteSpaceSeparatedTokens;

/**
 * Default implementation of {@link ExecBinding}.
 *
 * @see DefaultExecBinding#writeOutputInMessage(Message, ExecResult)
 */
public class DefaultExecBinding implements ExecBinding {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultExecBinding.class);

    @Override
    @SuppressWarnings("unchecked")
    public ExecCommand readInput(Exchange exchange, ExecEndpoint endpoint) {
        ObjectHelper.notNull(exchange, "exchange");
        ObjectHelper.notNull(endpoint, "endpoint");

        // do not convert args as we do that manually later
        Object args = exchange.getIn().removeHeader(EXEC_COMMAND_ARGS);
        String cmd = getAndRemoveHeader(exchange.getIn(), EXEC_COMMAND_EXECUTABLE, endpoint.getExecutable(), String.class);
        String dir = getAndRemoveHeader(exchange.getIn(), EXEC_COMMAND_WORKING_DIR, endpoint.getWorkingDir(), String.class);
        long timeout = getAndRemoveHeader(exchange.getIn(), EXEC_COMMAND_TIMEOUT, endpoint.getTimeout(), Long.class);
        String exitValuesString
                = getAndRemoveHeader(exchange.getIn(), EXEC_COMMAND_EXIT_VALUES, endpoint.getExitValues(), String.class);
        String outFilePath = getAndRemoveHeader(exchange.getIn(), EXEC_COMMAND_OUT_FILE, endpoint.getOutFile(), String.class);
        boolean useStderrOnEmptyStdout = getAndRemoveHeader(exchange.getIn(), EXEC_USE_STDERR_ON_EMPTY_STDOUT,
                endpoint.isUseStderrOnEmptyStdout(), Boolean.class);
        LoggingLevel commandLogLevel = getAndRemoveHeader(exchange.getIn(), EXEC_COMMAND_LOG_LEVEL,
                endpoint.getCommandLogLevel(), LoggingLevel.class);
        InputStream input = exchange.getIn().getBody(InputStream.class);

        // If the args is a list of strings already..
        List<String> argsList = null;
        if (isListOfStrings(args)) {
            argsList = (List<String>) args;
        }

        if (argsList == null) {
            // no we could not do that, then parse it as a string to a list
            String s = endpoint.getArgs();
            if (args != null) {
                // use args from header instead from endpoint
                s = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, args);
            }
            LOG.debug("Parsing argument String to a List: {}", s);
            argsList = splitToWhiteSpaceSeparatedTokens(s);
        }

        Set<Integer> exitValues = new HashSet<>();
        if (exitValuesString != null && exitValuesString.length() > 0) {
            exitValues = new HashSet<>(splitCommaSeparatedToListOfInts(exitValuesString));
        }

        File outFile = outFilePath == null ? null : new File(outFilePath);
        return new ExecCommand(
                cmd, argsList, dir, timeout, exitValues, input, outFile, useStderrOnEmptyStdout, commandLogLevel);
    }

    private boolean isListOfStrings(Object o) {
        if (o == null) {
            return false;
        }
        if (!(o instanceof List)) {
            return false;
        }
        @SuppressWarnings("rawtypes")
        List argsList = (List) o;
        for (Object s : argsList) {
            if (s.getClass() != String.class) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void writeOutput(Exchange exchange, ExecResult result) {
        ObjectHelper.notNull(exchange, "exchange");
        ObjectHelper.notNull(result, "result");

        if (exchange.getPattern().isOutCapable()) {
            writeOutputInMessage(exchange.getOut(), result);
            exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
        } else {
            writeOutputInMessage(exchange.getIn(), result);
        }
    }

    /**
     * Write the {@link ExecResult} in the message body. Write the stderr and the exit value for convenience in the
     * message headers. <br>
     * The stdout and/or resultFile should be accessible using a converter or using the result object directly.
     *
     * @param message a Camel message
     * @param result  an {@link ExecResult} instance
     */
    protected void writeOutputInMessage(Message message, ExecResult result) {
        message.setHeader(EXEC_STDERR, result.getStderr());
        message.setHeader(EXEC_EXIT_VALUE, result.getExitValue());
        message.setBody(result);
    }

    /**
     * Gets and removes the <code> <code>headerName</code> header form the input <code>message</code> (the header will
     * not be propagated)
     */
    protected <T> T getAndRemoveHeader(Message message, String headerName, T defaultValue, Class<T> headerType) {
        T h = message.getHeader(headerName, defaultValue, headerType);
        message.removeHeader(headerName);
        return h;
    }
}
