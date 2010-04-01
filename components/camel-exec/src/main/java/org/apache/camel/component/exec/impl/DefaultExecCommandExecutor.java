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
import java.util.List;

import org.apache.camel.component.exec.ExecCommand;
import org.apache.camel.component.exec.ExecCommandExecutor;
import org.apache.camel.component.exec.ExecEndpoint;
import org.apache.camel.component.exec.ExecException;
import org.apache.camel.component.exec.ExecResult;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Executes the command utilizing the <a
 * href="http://commons.apache.org/exec/">Apache Commmons exec library</a>. Adds
 * a shutdown hook for every executed process.
 */
public class DefaultExecCommandExecutor implements ExecCommandExecutor {

    private static final Log LOG = LogFactory.getLog(DefaultExecCommandExecutor.class);

    public ExecResult execute(ExecCommand command) {
        ObjectHelper.notNull(command, "command");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        ExecResult result = new ExecResult(command);

        DefaultExecutor executor = prepareDefaultExecutor(command);
        // handle error and output of the process and write them to the given
        // out stream
        PumpStreamHandler handler = new PumpStreamHandler(out, err, command.getInput());
        executor.setStreamHandler(handler);

        CommandLine cl = toCommandLine(command);

        try {
            int exitValue = executor.execute(cl);

            result.setExitValue(exitValue);
            result.setStdout(new ByteArrayInputStream(out.toByteArray()));
            result.setStderr(new ByteArrayInputStream(err.toByteArray()));
            return result;

        } catch (ExecuteException ee) {
            LOG.error("ExecuteExeption while executing " + command.toString());
            throw new ExecException("Failed to execute command " + command, ee);
        } catch (IOException ioe) {
            // invalid working dir
            LOG.error("IOException while executing " + command.toString());
            throw new ExecException("Unable to execute command " + command, ioe);
        } finally {
            // the inputStream must be closed after the execution
            IOUtils.closeQuietly(command.getInput());
        }
    }

    protected DefaultExecutor prepareDefaultExecutor(ExecCommand execCommand) {
        DefaultExecutor executor = new DefaultExecutor();
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
     * Transforms an exec command to a {@link CommandLine}
     * 
     * @param execCommand
     * @return a {@link CommandLine} object
     */
    protected CommandLine toCommandLine(ExecCommand execCommand) {
        CommandLine cl = new CommandLine(execCommand.getExecutable());
        List<String> args = execCommand.getArgs();
        for (String arg : args) {
            // do not handle quoting here, it is already quoted
            cl.addArgument(arg, false);
        }
        return cl;
    }
}
