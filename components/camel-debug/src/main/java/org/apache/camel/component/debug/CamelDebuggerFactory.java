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
package org.apache.camel.component.debug;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.debugger.BacklogDebugger;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.DebuggerFactory;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.LifecycleStrategySupport;

@JdkService(Debugger.FACTORY)
public class CamelDebuggerFactory implements DebuggerFactory {

    @Override
    public Debugger createDebugger(CamelContext camelContext) throws Exception {
        // must enable message history for debugger to capture more details
        camelContext.setMessageHistory(true);
        // must enable source location so debugger tooling knows to map breakpoints to source code
        camelContext.setSourceLocationEnabled(true);

        BacklogDebugger backlog = BacklogDebugger.createDebugger(camelContext);
        // we need to enable debugger after context is started
        camelContext.addLifecycleStrategy(new LifecycleStrategySupport() {
            @Override
            public void onContextStarted(CamelContext context) {
                backlog.enableDebugger();
            }
        });
        camelContext.addService(backlog);

        // to make debugging possible for tooling we need to make it possible to do remote JMX connection
        camelContext.addService(new JmxConnectorService());

        // return null as we fool camel-core into using this backlog debugger as we added it as a service
        return null;
    }

    @Override
    public String toString() {
        return "camel-debug";
    }
}
