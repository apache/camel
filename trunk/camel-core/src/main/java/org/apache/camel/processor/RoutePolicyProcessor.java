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

import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.impl.SynchronizationAdapter;
import org.apache.camel.spi.RoutePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Processor} which instruments the {@link RoutePolicy}.
 *
 * @version 
 */
public class RoutePolicyProcessor extends DelegateAsyncProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(RoutePolicyProcessor.class);
    private final List<RoutePolicy> routePolicies;
    private Route route;

    public RoutePolicyProcessor(Processor processor, List<RoutePolicy> routePolicies) {
        super(processor);
        this.routePolicies = routePolicies;
    }

    @Override
    public String toString() {
        return "RoutePolicy[" + routePolicies + "]";
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {

        // invoke begin
        for (RoutePolicy policy : routePolicies) {
            try {
                if (isRoutePolicyRunAllowed(policy)) {
                    policy.onExchangeBegin(route, exchange);
                }
            } catch (Exception e) {
                LOG.warn("Error occurred during onExchangeBegin on RoutePolicy: " + policy
                        + ". This exception will be ignored", e);
            }
        }

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

                for (RoutePolicy policy : routePolicies) {
                    try {
                        if (isRoutePolicyRunAllowed(policy)) {
                            policy.onExchangeDone(route, exchange);
                        }
                    } catch (Exception e) {
                        LOG.warn("Error occurred during onExchangeDone on RoutePolicy: " + policy
                                + ". This exception will be ignored", e);
                    }
                }
            }

            @Override
            public String toString() {
                return "RoutePolicyOnCompletion";
            }
        });

        return super.process(exchange, callback);
    }

    /**
     * Sets the route this policy applies.
     *
     * @param route the route
     */
    public void setRoute(Route route) {
        this.route = route;
    }

    /**
     * Strategy to determine if this policy is allowed to run
     *
     * @param policy the policy
     * @return <tt>true</tt> to run
     */
    protected boolean isRoutePolicyRunAllowed(RoutePolicy policy) {
        if (policy instanceof ServiceSupport) {
            ServiceSupport ss = (ServiceSupport) policy;
            return ss.isRunAllowed();
        }
        return true;
    }

    private static boolean isCamelStopping(CamelContext context) {
        if (context instanceof ServiceSupport) {
            ServiceSupport ss = (ServiceSupport) context;
            return ss.isStopping() || ss.isStopped();
        }
        return false;
    }

}
