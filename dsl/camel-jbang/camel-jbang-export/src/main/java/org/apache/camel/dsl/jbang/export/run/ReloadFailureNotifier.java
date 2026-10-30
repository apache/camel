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
package org.apache.camel.dsl.jbang.export.run;

import java.util.List;

import org.apache.camel.impl.event.CamelContextReloadFailureEvent;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.SimpleEventNotifierSupport;

/**
 * Tells the context of a run in this JVM when a route file fails to reload in dev mode
 * ({@link RunContext#onReloadFailure(Throwable, List)}).
 */
final class ReloadFailureNotifier extends SimpleEventNotifierSupport {

    private final RunContext context;

    ReloadFailureNotifier(RunContext context) {
        this.context = context;
        setIgnoreExchangeEvents(true);
        setIgnoreServiceEvents(true);
        // EventHelper.notifyContextReloadFailure skips notifiers where isIgnoreRouteEvents() is true;
        // leave ignoreRouteEvents at its default (false) so CamelContextReloadFailureEvent reaches us.
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
        return event instanceof CamelEvent.CamelContextReloadFailureEvent;
    }

    @Override
    public void notify(CamelEvent event) {
        // the file watcher emits the event with the file name as the action (the API's getSource() is the context)
        if (event instanceof CamelContextReloadFailureEvent failure && failure.getAction() instanceof String name) {
            context.onReloadFailure(failure.getCause(), List.of(name));
        }
    }
}
