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
package org.apache.camel.component.log;

import org.apache.camel.Component;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.util.ServiceHelper;

/**
 * Logger endpoint.
 */
public class LogEndpoint extends ProcessorEndpoint {

    private Processor logger;

    public LogEndpoint() {
    }

    public LogEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public LogEndpoint(String endpointUri, Component component, Processor logger) {
        super(endpointUri, component);
        setLogger(logger);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(logger);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(logger);
    }

    public void setLogger(Processor logger) {
        this.logger = logger;
        // the logger is the processor
        setProcessor(this.logger);
    }

    public Processor getLogger() {
        return logger;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new LogProducer(this, this.logger);
    }

    @Override
    protected String createEndpointUri() {
        return "log:" + logger.toString();
    }
}
