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
package org.apache.camel.sanitizer;

import org.apache.camel.Exchange;
import org.apache.camel.spi.OnCompletionListener;
import org.apache.camel.spi.StepListener;
import org.apache.camel.spi.Synchronization;

/**
 * Rule R3 of the exchange sanitizer, its second part: the points where the routing engine uses an exchange, for the
 * sampled watch windows of {@link Overlaps}. A step is called ({@link #onStepCall}, before its processor), the callback
 * of a step continues the exchange ({@link #onStepCallback}, before the callback goes on), and an on completion runs
 * ({@link #onCompletionRun}, before it runs). The return of {@code process} and its throw are not points.
 * <p/>
 * It is installed after the step listeners of R1 and of the contract of R3, so at a callback they have reported a
 * violation of the call before this thread comes to its point. It is installed only when the sampling is on
 * ({@code camel.sanitizer.overlapSampling} more than 0), so with the sampling off it costs nothing.
 */
final class SanitizerOverlapListener implements StepListener, OnCompletionListener {

    private final Overlaps overlaps;

    SanitizerOverlapListener(Overlaps overlaps) {
        this.overlaps = overlaps;
    }

    @Override
    public Object onStepCall(Exchange exchange, String routeId, String nodeId) {
        // only here may the exchange be read: the callback can come on a thread that does not own it yet
        Point point = new Point(exchange.getExchangeId(), System.identityHashCode(exchange), routeId, nodeId);
        overlaps.point(point.exchangeId, point.identity, Overlaps.STEP_CALL, routeId, nodeId);
        return point;
    }

    @Override
    public void onStepCallback(Object state, boolean doneSync) {
        if (state instanceof Point point) {
            overlaps.point(point.exchangeId, point.identity, Overlaps.STEP_CALLBACK, point.routeId, point.nodeId);
        }
    }

    @Override
    public void onCompletionRun(Exchange exchange, Synchronization onCompletion, boolean failure) {
        // on the thread that runs the on completions of the exchange, which owns it then
        overlaps.point(exchange.getExchangeId(), System.identityHashCode(exchange), Overlaps.ON_COMPLETION,
                exchange.getFromRouteId(), onCompletion.getClass().getName());
    }

    @Override
    public String toString() {
        return "ExchangeSanitizer[R3 overlaps]";
    }

    /**
     * The step of a call and the id and identity hash of its exchange, for the point of its callback (no exchange is
     * kept).
     */
    private record Point(String exchangeId, int identity, String routeId, String nodeId) {
    }
}
