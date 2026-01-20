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
package org.apache.camel.component.cli.debug;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.debugger.DefaultBacklogDebugger;
import org.apache.camel.spi.BacklogDebugger;
import org.apache.camel.spi.CliConnector;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.DebuggerFactory;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService(Debugger.FACTORY)
public class CamelCliDebuggerFactory implements DebuggerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(CamelCliDebuggerFactory.class);

    private final AtomicBoolean hangupIntercepted = new AtomicBoolean();

    @Override
    // Debugger is created and added as a service. This method always returns a null object.
    public Debugger createDebugger(CamelContext camelContext) throws Exception {
        // only create a debugger if none already exists
        if (camelContext.hasService(BacklogDebugger.class) == null) {

            // NOTE: the AutoCloseable object is added as a Service, hence it is closed by Camel context
            // according to the object lifecycle.
            BacklogDebugger backlog = DefaultBacklogDebugger.createDebugger(camelContext); // NOSONAR
            backlog.setStandby(true);
            backlog.setLoggingLevel("DEBUG");
            backlog.setSingleStepIncludeStartEnd(true);
            backlog.setInitialBreakpoints(BacklogDebugger.BREAKPOINT_ALL_ROUTES);
            backlog.setSuspendMode(true); // wait for attach via CLI

            // must enable source location and history
            // so debugger tooling knows to map breakpoints to source code
            camelContext.setSourceLocationEnabled(true);
            camelContext.setMessageHistory(true);

            // enable debugger on camel
            camelContext.setDebugging(true);

            // we need to enable debugger after context is started
            camelContext.addLifecycleStrategy(new LifecycleStrategySupport() {
                @Override
                public void onContextStarted(CamelContext context) {
                    // noop
                }

                @Override
                public void onContextStopping(CamelContext context) {
                    backlog.detach();
                    backlog.disableDebugger();
                }
            });
            camelContext.addService(backlog, true, true);

            // need to make debugger faster
            CliConnector cli = camelContext.hasService(CliConnector.class);
            if (cli != null) {
                cli.updateDelay(100);
            }

            installHangupInterceptor();

            long pid = ProcessHandle.current().pid();

            LOG.info("=".repeat(80));
            LOG.info("Waiting for CLI to remote attach: camel debug --remote-attach --name={}", pid);
            StopWatch watch = new StopWatch();
            while (!hangupIntercepted.get() && !backlog.isEnabled() && !camelContext.isStopping()) {
                try {
                    Thread.sleep(1000);
                    if (watch.taken() > 10000) {
                        LOG.info("Waiting for CLI to remote attach: camel debug --remote-attach --name={}", pid);
                        watch.restart();
                    }
                } catch (InterruptedException e) {
                    return null;
                }
            }
            if (backlog.isEnabled()) {
                LOG.info("CLI remote debugger attached");
            }
        }

        // return null as we fool camel-core into using this backlog debugger as we added it as a service
        return null;
    }

    @Override
    public String toString() {
        return "camel-cli-debug";
    }

    private void handleHangup() {
        hangupIntercepted.set(true);
    }

    private void installHangupInterceptor() {
        Thread task = new Thread(this::handleHangup);
        task.setName(ThreadHelper.resolveThreadName(null, "CamelCliDebuggerHangupInterceptor"));
        Runtime.getRuntime().addShutdownHook(task);
    }

}
