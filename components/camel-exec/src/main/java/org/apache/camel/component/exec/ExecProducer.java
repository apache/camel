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

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Exec producer
 * 
 * @see {link Producer}
 */
public class ExecProducer extends DefaultProducer {

    private final Log log;

    private final ExecEndpoint endpoint;

    public ExecProducer(ExecEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.log = LogFactory.getLog(ExecProducer.class);
    }

    public void process(Exchange exchange) throws Exception {
        ExecCommand execCommand = getBinding().readInput(exchange, endpoint);

        if (log.isInfoEnabled()) {
            log.info("Executing " + execCommand);
        }
        ExecResult result = endpoint.getCommandExecutor().execute(execCommand);
        ObjectHelper.notNull(result, "The command executor must return a not-null result");
        log(result);
        getBinding().writeOutput(exchange, result);
    }

    private ExecBinding getBinding() {
        return endpoint.getBinding();
    }

    private void log(ExecResult result) {
        String executable = result.getCommand().getExecutable();
        // 0 means no error, by convention
        if (result.getExitValue() != 0) {
            if (log.isErrorEnabled()) {
                log.error(new StringBuilder(executable).append(" exit value: ").append(result.getExitValue()));
                log.error(new StringBuilder(executable).append(" stderr: ").append(result.getStderr()));
            }
        }
        if (log.isDebugEnabled()) {
            log.debug(new StringBuilder(executable).append(" stdout: ").append(result.getStdout()));
        }

    }
}
