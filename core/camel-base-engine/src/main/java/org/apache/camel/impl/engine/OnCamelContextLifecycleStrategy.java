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
package org.apache.camel.impl.engine;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.OnCamelContextInitialized;
import org.apache.camel.spi.OnCamelContextInitializing;
import org.apache.camel.spi.OnCamelContextStarted;
import org.apache.camel.spi.OnCamelContextStarting;
import org.apache.camel.spi.OnCamelContextStopped;
import org.apache.camel.spi.OnCamelContextStopping;
import org.apache.camel.support.LifecycleStrategySupport;

/**
 * {@link LifecycleStrategy} for invoking callbacks {@link OnCamelContextInitialized}, {@link OnCamelContextStart}, and
 * {@link OnCamelContextStop} which has been registered in the Camel {@link org.apache.camel.spi.Registry}.
 */
class OnCamelContextLifecycleStrategy extends LifecycleStrategySupport {

    @Override
    public void onContextInitializing(CamelContext context) throws VetoCamelContextStartException {
        for (OnCamelContextInitializing handler : context.getRegistry().findByType(OnCamelContextInitializing.class)) {
            // RoutesBuilder should register them-self to the camel context
            // to avoid invoking them multiple times if routes are discovered
            // from the registry (i.e. camel-main)
            if (!(handler instanceof RoutesBuilder)) {
                handler.onContextInitializing(context);
            }
        }
    }

    @Override
    public void onContextInitialized(CamelContext context) throws VetoCamelContextStartException {
        for (OnCamelContextInitialized handler : context.getRegistry().findByType(OnCamelContextInitialized.class)) {
            // RoutesBuilder should register them-self to the camel context
            // to avoid invoking them multiple times if routes are discovered
            // from the registry (i.e. camel-main)
            if (!(handler instanceof RoutesBuilder)) {
                handler.onContextInitialized(context);
            }
        }
    }

    @Override
    public void onContextStarting(CamelContext context) throws VetoCamelContextStartException {
        for (OnCamelContextStarting handler : context.getRegistry().findByType(OnCamelContextStarting.class)) {
            // RoutesBuilder should register them-self to the camel context
            // to avoid invoking them multiple times if routes are discovered
            // from the registry (i.e. camel-main)
            if (!(handler instanceof RoutesBuilder)) {
                handler.onContextStarting(context);
            }
        }
    }

    @Override
    public void onContextStarted(CamelContext context) {
        for (OnCamelContextStarted handler : context.getRegistry().findByType(OnCamelContextStarted.class)) {
            // RoutesBuilder should register them-self to the camel context
            // to avoid invoking them multiple times if routes are discovered
            // from the registry (i.e. camel-main)
            if (!(handler instanceof RoutesBuilder)) {
                handler.onContextStarted(context);
            }
        }
    }

    @Override
    public void onContextStopping(CamelContext context) {
        for (OnCamelContextStopping handler : context.getRegistry().findByType(OnCamelContextStopping.class)) {
            // RoutesBuilder should register them-self to the camel context
            // to avoid invoking them multiple times if routes are discovered
            // from the registry (i.e. camel-main)
            if (!(handler instanceof RoutesBuilder)) {
                handler.onContextStopping(context);
            }
        }
    }

    @Override
    public void onContextStopped(CamelContext context) {
        for (OnCamelContextStopped handler : context.getRegistry().findByType(OnCamelContextStopped.class)) {
            // RoutesBuilder should register them-self to the camel context
            // to avoid invoking them multiple times if routes are discovered
            // from the registry (i.e. camel-main)
            if (!(handler instanceof RoutesBuilder)) {
                handler.onContextStopped(context);
            }
        }
    }

}
