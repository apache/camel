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

import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.spi.OnCompletionListener;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.UnitOfWork;

/**
 * Rule R2 of the exchange sanitizer: each commit of a consumer runs exactly once, for the unit of work that owns it
 * (the contract described on {@link Synchronization}). It follows the commits in {@link Commits}, which keeps no
 * exchange: the exchanges are read here only, on the thread that makes the change.
 */
final class SanitizerOnCompletionListener implements OnCompletionListener {

    private final Sanitizer sanitizer;
    private final Commits commits;

    SanitizerOnCompletionListener(Sanitizer sanitizer) {
        this.sanitizer = sanitizer;
        this.commits = sanitizer.getCommits();
    }

    @Override
    public void onUnitOfWork(Exchange exchange, UnitOfWork unitOfWork, List<Synchronization> moved) {
        Endpoint from = exchange.getFromEndpoint();
        commits.unitOfWork(exchange.getExchangeId(), exchange.getFromRouteId(), from != null ? from.getEndpointUri() : null,
                unitOfWork, moved);
    }

    @Override
    public void onCompletionAdded(UnitOfWork unitOfWork, Synchronization onCompletion) {
        commits.added(unitOfWork);
    }

    @Override
    public void onCompletionRemoved(UnitOfWork unitOfWork, Synchronization onCompletion) {
        commits.removed(unitOfWork, onCompletion);
    }

    @Override
    public void onCompletionHandover(
            Exchange from, UnitOfWork fromUnitOfWork, Exchange to, List<Synchronization> moved) {
        Commits.Handover handover = commits.handover(from.getExchangeId(), fromUnitOfWork, to.getExchangeId(),
                to.getUnitOfWork(), moved, sanitizer.currentStep());
        if (handover != null) {
            // the stack is taken outside the lock of the table
            Sanitizer.Trace trace = sanitizer.trace();
            if (!handover.fromUnitOfWork.isEmpty()) {
                commits.handedOver(handover, trace);
            }
            for (Commits.Finding f : handover.found) {
                f.trace = trace;
                sanitizer.recordCommit(f);
            }
            sanitizer.commitsDropped();
        }
    }

    @Override
    public void onCompletionTaken(Exchange from, List<Synchronization> taken) {
        commits.taken(from.getExchangeId(), taken);
    }

    @Override
    public void onCompletionRun(Exchange exchange, Synchronization onCompletion, boolean failure) {
        Commits.Run run = commits.run(exchange.getExchangeId(), exchange.getUnitOfWork(), onCompletion);
        if (run != null) {
            // the stack is taken outside the lock of the table
            Sanitizer.Trace trace = sanitizer.trace();
            if (run.found() == null) {
                commits.ranFirst(run.commit(), trace);
            } else {
                run.found().trace = trace;
                sanitizer.recordCommit(run.found());
            }
        }
    }

    @Override
    public String toString() {
        return "ExchangeSanitizer[R2]";
    }
}
