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
package org.apache.camel.component.exec.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import org.apache.camel.component.exec.ExecCommand;
import org.apache.camel.component.exec.ExecCommandExecutor;
import org.apache.camel.component.exec.ExecDefaultExecutor;
import org.apache.camel.component.exec.ExecEndpoint;
import org.apache.camel.component.exec.ExecException;
import org.apache.camel.component.exec.ExecResult;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Executes the command utilizing the <a
 * href="http://commons.apache.org/exec/">Apache Commons exec library</a>. Adds
 * a shutdown hook for every executed process.
 */
public class DefaultExecCommandExecutor implements ExecCommandExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultExecCommandExecutor.class);

    @Override
    public ExecResult execute(ExecCommand command) {
        notNull(command, "command");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        DefaultExecutor executor = prepareDefaultExecutor(command);
        // handle error and output of the process and write them to the given
        // out stream
        PumpStreamHandler handler = new PumpStreamHandler(out, err, command.getInput());
        executor.setStreamHandler(handler);

        CommandLine cl = toCommandLine(command);

        try {
            int exitValue = executor.execute(cl);
            // if the size is zero, we have no output, so construct the result
            // with null (required by ExecResult)
            InputStream stdout = out.size() == 0 ? null : new ByteArrayInputStream(out.toByteArray());
            InputStream stderr = err.size() == 0 ? null : new ByteArrayInputStream(err.toByteArray());
            ExecResult result = new ExecResult(command, stdout, stderr, exitValue);
            return result;

        } catch (ExecuteException ee) {
            LOG.error("ExecException while executing command: " + command.toString() + " - " + ee.getMessage());

            InputStream stdout = out.size() == 0 ? null : new ByteArrayInputStream(out.toByteArray());
            InputStream stderr = err.size() == 0 ? null : new ByteArrayInputStream(err.toByteArray());

            throw new ExecException("Failed to execute command " + command, stdout, stderr, ee.getExitValue(), ee);

        } catch (IOException ioe) {

            InputStream stdout = out.size() == 0 ? null : new ByteArrayInputStream(out.toByteArray());
            InputStream stderr = err.size() == 0 ? null : new ByteArrayInputStream(err.toByteArray());

            int exitValue = 0; // use 0 as exit value as the executor didn't return the value
            if (executor instanceof ExecDefaultExecutor) {
                // get the exit value from the executor as it captures this to work around the common-exec bug
                exitValue = ((ExecDefaultExecutor) executor).getExitValue();
            }

            // workaround to ignore if the stream was already closes due some race condition in commons-exec
            String msg = ioe.getMessage();
            if (msg != null && "stream closed".equals(msg.toLowerCase(Locale.ENGLISH))) {
                LOG.debug("Ignoring Stream closed IOException", ioe);

                ExecResult result = new ExecResult(command, stdout, stderr, exitValue);
                return result;
            }
            // invalid working dir
            LOG.error("IOException while executing command: " + command.toString() + " - " + ioe.getMessage());
            throw new ExecException("Unable to execute command " + command, stdout, stderr, exitValue, ioe);
        } finally {
            // the inputStream must be closed after the execution
            IOUtils.closeQuietly(command.getInput());
        }
    }

    protected DefaultExecutor prepareDefaultExecutor(ExecCommand execCommand) {
        DefaultExecutor executor = new ExecDefaultExecutor();
        executor.setExitValues(null);

        if (execCommand.getWorkingDir() != null) {
            executor.setWorkingDirectory(new File(execCommand.getWorkingDir()).getAbsoluteFile());
        }
        if (execCommand.getTimeout() != ExecEndpoint.NO_TIMEOUT) {
            executor.setWatchdog(new ExecuteWatchdog(execCommand.getTimeout()));
        }
        executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());
        return executor;
    }

    /**
     * Transforms an {@link ExecCommand} to a {@link CommandLine}. No quoting fo
     * the arguments is used.
     *
     * @param execCommand a not-null <code>ExecCommand</code> instance.
     * @return a {@link CommandLine} object.
     */
    protected CommandLine toCommandLine(ExecCommand execCommand) {
        notNull(execCommand, "execCommand");
        CommandLine cl = new CommandLine(execCommand.getExecutable());
        List<String> args = execCommand.getArgs();
        for (String arg : args) {
            // do not handle quoting here, it is already quoted
            cl.addArgument(arg, false);
        }
        return cl;
    }
}
