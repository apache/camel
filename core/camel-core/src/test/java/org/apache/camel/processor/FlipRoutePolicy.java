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
package org.apache.camel.processor;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.support.RoutePolicySupport;

public class FlipRoutePolicy extends RoutePolicySupport {

    private final String name1;
    private final String name2;

    /**
     * Flip the two routes
     *
     * @param name1 name of the first route
     * @param name2 name of the second route
     */
    public FlipRoutePolicy(String name1, String name2) {
        this.name1 = name1;
        this.name2 = name2;
    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        // decide which route to stop and start
        // basically we should flip the two routes
        String stop = route.getId().equals(name1) ? name1 : name2;
        String start = route.getId().equals(name1) ? name2 : name1;

        FlipThread thread = new FlipThread(exchange.getContext(), start, stop);
        thread.start();
    }

    /**
     * Use a thread to flip the routes.
     */
    private final class FlipThread extends Thread {

        private final CamelContext context;
        private final String start;
        private final String stop;

        private FlipThread(CamelContext context, String start, String stop) {
            this.context = context;
            this.start = start;
            this.stop = stop;
        }

        @Override
        public void run() {
            try {
                context.getRouteController().stopRoute(stop);
                context.getRouteController().startRoute(start);
            } catch (Exception e) {
                // let the exception handle handle it, which is often just to
                // log it
                getExceptionHandler().handleException("Error flipping routes", e);
            }
        }
    }

}
