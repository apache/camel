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
package org.apache.camel.component.micrometer.eventnotifier;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.security.AuthorizationDecisionEvent;
import org.apache.camel.support.security.AuthorizationDecisionEvent.Outcome;

/**
 * Optional metrics for authorization observations. Every label is a fixed enum; correlation and revisions are omitted.
 */
public class MicrometerAuthorizationEventNotifier extends AbstractMicrometerEventNotifier<AuthorizationDecisionEvent> {
    public MicrometerAuthorizationEventNotifier() {
        super(AuthorizationDecisionEvent.class);
    }

    @Override
    public void notify(CamelEvent event) {
        AuthorizationDecisionEvent decision = (AuthorizationDecisionEvent) event;
        Tags common = Tags.of("engine", decision.getEngine().name(), "operation", decision.getOperation().name());
        Timer.builder("camel.authorization.duration")
                .tags(common.and("outcome", decision.getOutcome().name(), "failure", decision.getFailure().name()))
                .register(getMeterRegistry()).record(decision.getDurationNanos(), TimeUnit.NANOSECONDS);
        var counts = decision.getCounts();
        count(common, Outcome.ALLOW, counts.allowed());
        count(common, Outcome.DENY, counts.denied());
        count(common, Outcome.ERROR, counts.errors());
        count(common, Outcome.UNAVAILABLE, counts.unavailable());
        count(common, Outcome.FAILED_OPEN, counts.failedOpen());
        if (decision.getPoolWaitNanos() >= 0) {
            Timer.builder("camel.authorization.pool.wait").tags(common).register(getMeterRegistry())
                    .record(decision.getPoolWaitNanos(), TimeUnit.NANOSECONDS);
        }
    }

    private void count(Tags common, Outcome outcome, long count) {
        if (count > 0) {
            Counter.builder("camel.authorization.decisions").tags(common.and("outcome", outcome.name()))
                    .register(getMeterRegistry()).increment(count);
        }
    }
}
