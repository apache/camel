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
package org.apache.camel.processor;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.impl.SynchronizationAdapter;
import org.apache.camel.spi.RoutePolicy;

/**
 * @version $Revision$
 */
public class RoutePolicyProcessor extends DelegateProcessor {

    private final RoutePolicy routePolicy;
    private Route route;

    public RoutePolicyProcessor(Processor processor, RoutePolicy routePolicy) {
        super(processor);
        this.routePolicy = routePolicy;
    }

    @Override
    public String toString() {
        return "RoutePolicy[" + routePolicy + "]";
    }

    @Override
    protected void processNext(Exchange exchange) throws Exception {
        // check whether the policy is enabled
        if (isRoutePolicyRunAllowed()) {

            // invoke begin
            routePolicy.onExchangeBegin(route, exchange);

            // add on completion that invokes the policy callback on complete
            // as the Exchange can be routed async and thus we need the callback to
            // invoke when the route is completed
            exchange.addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onDone(Exchange exchange) {
                    // do not invoke it if Camel is stopping as we don't want
                    // the policy to start a consumer during Camel is stopping
                    if (isCamelStopping(exchange.getContext())) {
                        return;
                    }
                    routePolicy.onExchangeDone(route, exchange);
                }
            });
        }

        if (processor != null) {
            processor.process(exchange);
        }
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    private static boolean isCamelStopping(CamelContext context) {
        if (context instanceof ServiceSupport) {
            ServiceSupport ss = (ServiceSupport) context;
            return ss.isStopping() || ss.isStopped();
        }
        return false;
    }

    private boolean isRoutePolicyRunAllowed() {
        if (routePolicy instanceof ServiceSupport) {
            ServiceSupport ss = (ServiceSupport) routePolicy;
            return ss.isRunAllowed();
        }
        return true;
    }

}
