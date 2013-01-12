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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.UnitOfWork;

/**
 * This processor tracks the current {@link RouteContext} while processing the {@link Exchange}.
 * This ensures that the {@link Exchange} have details under which route its being currently processed.
 */
public class RouteContextProcessor extends DelegateAsyncProcessor {

    private final RouteContext routeContext;

    public RouteContextProcessor(RouteContext routeContext, Processor processor) {
        super(processor);
        this.routeContext = routeContext;
    }

    @Override
    protected boolean processNext(final Exchange exchange, final AsyncCallback callback) {
        // push the current route context
        final UnitOfWork unitOfWork = exchange.getUnitOfWork();
        if (unitOfWork != null) {
            unitOfWork.pushRouteContext(routeContext);
        }

        boolean sync = processor.process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                try {
                    // pop the route context we just used
                    if (unitOfWork != null) {
                        unitOfWork.popRouteContext();
                    }
                } catch (Exception e) {
                    exchange.setException(e);
                } finally {
                    callback.done(doneSync);
                }
            }
        });

        return sync;
    }

    @Override
    public String toString() {
        return "RouteContextProcessor[" + processor + "]";
    }

}
