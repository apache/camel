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

package org.apache.camel.test.junit6.patterns;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.test.junit6.DebugBreakpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestDebugBreakpoint extends DebugBreakpoint {
    private static final Logger LOG = LoggerFactory.getLogger(TestDebugBreakpoint.class);
    private boolean debugBeforeCalled = false;
    private boolean debugAfterCalled = false;

    @Override
    protected void debugBefore(
            Exchange exchange, Processor processor, ProcessorDefinition<?> definition, String id, String label) {
        // this method is invoked before we are about to enter the given
        // processor
        // from your Java editor you can add a breakpoint in the code line
        // below
        LOG.info("Before {} with body {}", definition, exchange.getIn().getBody());
        debugBeforeCalled = true;
    }

    @Override
    protected void debugAfter(
            Exchange exchange,
            Processor processor,
            ProcessorDefinition<?> definition,
            String id,
            String label,
            long timeTaken) {

        debugAfterCalled = true;
    }

    public boolean isDebugBeforeCalled() {
        return debugBeforeCalled;
    }

    public boolean isDebugAfterCalled() {
        return debugAfterCalled;
    }
}
