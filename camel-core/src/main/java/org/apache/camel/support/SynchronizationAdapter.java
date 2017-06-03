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
package org.apache.camel.support;

import org.apache.camel.Exchange;
import org.apache.camel.Ordered;
import org.apache.camel.Route;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.SynchronizationRouteAware;
import org.apache.camel.spi.SynchronizationVetoable;

/**
 * Simple {@link Synchronization} adapter with empty methods for easier overriding
 * of single methods.
 *
 * @version 
 */
public class SynchronizationAdapter implements SynchronizationVetoable, Ordered, SynchronizationRouteAware {

    public void onComplete(Exchange exchange) {
        onDone(exchange);
    }

    public void onFailure(Exchange exchange) {
        onDone(exchange);
    }

    public void onDone(Exchange exchange) {
        // noop
    }

    public boolean allowHandover() {
        // allow by default
        return true;
    }

    public int getOrder() {
        // no particular order by default
        return 0;
    }

    public void onBeforeRoute(Route route, Exchange exchange) {
        // noop
    }

    public void onAfterRoute(Route route, Exchange exchange) {
        // noop
    }
}
