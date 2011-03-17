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
import org.apache.camel.Producer;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.processor.CamelLogger;
import org.apache.camel.util.ServiceHelper;

/**
 * Logger endpoint.
 */
public class LogEndpoint extends ProcessorEndpoint {

    private CamelLogger logger;

    public LogEndpoint() {
    }

    public LogEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public LogEndpoint(String endpointUri, Component component, CamelLogger logger) {
        super(endpointUri, component);
        setLogger(logger);
    }
    
    public CamelLogger getLogger() {
        return logger;
    }

    @Override
    public void start() throws Exception {
        ServiceHelper.startService(logger);
    }

    @Override
    public void stop() throws Exception {
        ServiceHelper.stopService(logger);
    }
    
    public void setLogger(CamelLogger logger) {
        this.logger = logger;
        // the logger is the processor
        setProcessor(this.logger);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new LogProducer(this, getLogger());
    }

    @Override
    protected String createEndpointUri() {
        return "log:" + logger.toString();
    }
}
