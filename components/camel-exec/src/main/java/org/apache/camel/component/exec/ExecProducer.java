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
import org.apache.camel.component.exec.impl.DefaultExecCommandExecutor;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exec producer.
 * 
 * @see {link Producer}
 */
public class ExecProducer extends DefaultProducer {

    private final Logger log;

    private final ExecEndpoint endpoint;

    public ExecProducer(ExecEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.log = LoggerFactory.getLogger(ExecProducer.class);
    }

    public void process(Exchange exchange) throws Exception {
        ExecCommand execCommand = getBinding().readInput(exchange, endpoint);

        ExecCommandExecutor executor = endpoint.getCommandExecutor();
        if (executor == null) {
            // create a new non-shared executor
            executor = new DefaultExecCommandExecutor();
        }

        log.info("Executing {}", execCommand);
        ExecResult result = executor.execute(execCommand);

        ObjectHelper.notNull(result, "The command executor must return a not-null result");
        log.info("The command {} had exit value {}", execCommand, result.getExitValue());
        if (result.getExitValue() != 0) {
            log.error("The command {} returned exit value {}", execCommand, result.getExitValue());
        }
        getBinding().writeOutput(exchange, result);
    }

    private ExecBinding getBinding() {
        return endpoint.getBinding();
    }
}
