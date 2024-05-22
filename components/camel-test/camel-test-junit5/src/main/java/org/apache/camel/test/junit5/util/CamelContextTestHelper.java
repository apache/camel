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

package org.apache.camel.test.junit5.util;

import org.apache.camel.CamelContext;
import org.apache.camel.RouteConfigurationsBuilder;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ServiceStatus;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.debugger.DefaultDebugger;
import org.apache.camel.spi.Breakpoint;
import org.apache.camel.spi.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CamelContextTestHelper {
    private static final Logger LOG = LoggerFactory.getLogger(CamelContextTestHelper.class);

    public static CamelContext createCamelContext(Registry registry) throws Exception {
        CamelContext retContext;
        if (registry != null) {
            retContext = new DefaultCamelContext(registry);
        } else {
            retContext = new DefaultCamelContext();
        }

        return retContext;
    }

    public static void setupDebugger(CamelContext context, Breakpoint breakpoint) {
        assert context != null : "You cannot set a debugger on a null context";
        assert breakpoint != null : "You cannot set a debugger using a null debug breakpoint";

        if (context.getStatus().equals(ServiceStatus.Started)) {
            LOG.info("Cannot setting the Debugger to the starting CamelContext, stop the CamelContext now.");
            // we need to stop the context first to setup the debugger
            context.stop();
        }
        context.setDebugging(true);
        final DefaultDebugger defaultDebugger = new DefaultDebugger();
        context.setDebugger(defaultDebugger);

        defaultDebugger.addBreakpoint(breakpoint);
        // when stopping CamelContext it will automatically remove the breakpoint
    }

    public static void setupRoutes(CamelContext context, RoutesBuilder[] builders) throws Exception {
        // add configuration before routes
        for (RoutesBuilder builder : builders) {
            if (builder instanceof RouteConfigurationsBuilder) {
                LOG.debug("Using created route configuration: {}", builder);
                context.addRoutesConfigurations((RouteConfigurationsBuilder) builder);
            }
        }
        for (RoutesBuilder builder : builders) {
            LOG.debug("Using created route builder to add routes: {}", builder);
            context.addRoutes(builder);
        }
        for (RoutesBuilder builder : builders) {
            LOG.debug("Using created route builder to add templated routes: {}", builder);
            context.addTemplatedRoutes(builder);
        }
    }
}
