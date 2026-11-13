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

/**
 * The kinds of violation the exchange sanitizer reports. Rule R1: a break of the contract of
 * {@link org.apache.camel.AsyncProcessor#process(org.apache.camel.Exchange, org.apache.camel.AsyncCallback)} by the
 * processor of a step of a route (the callback of a step is called exactly once, and {@code process} does not throw).
 * Rule R2: a break of the contract of the commit of a consumer (described on
 * {@link org.apache.camel.spi.Synchronization}: each commit runs exactly once, for the unit of work that owns it).
 *
 * @since 4.24
 */
public enum Violation {

    /**
     * The callback of a call of the processor of a step was called a second time (or more).
     */
    COMPLETED_TWICE("callback called twice", "let only the first path call the callback (for example with a"
                                             + " compareAndSet on the outcome)"),

    /**
     * {@code process(exchange, callback)} threw, and the callback of the call had not been called.
     */
    THREW_BEFORE_CALLBACK("process threw before calling the callback", "catch the exception, set it on the exchange"
                                                                       + " (exchange.setException) and call the callback"),

    /**
     * {@code process(exchange, callback)} threw after the callback of the call had been called.
     */
    THREW_AFTER_CALLBACK("process threw after calling the callback", "do not throw after the callback was called; set"
                                                                     + " the exception on the exchange before calling the callback"),

    /**
     * The callback of a call was called after {@code process(exchange, callback)} of that call threw. Camel fails the
     * exchange with what was thrown, so this callback and Camel's failure race to continue the exchange.
     */
    CALLBACK_AFTER_THROW("callback called after process threw", "either call the callback or throw, never both: catch"
                                                                + " the exception, set it on the exchange and call the callback once"),

    /**
     * Rule R2: the commit of a consumer ran a second time (onComplete or onFailure), for one add to an exchange.
     */
    COMMIT_RUN_TWICE("commit ran a second time", "R2", Fixes.RUN_TWICE),

    /**
     * Rule R2: the commit of a consumer was handed over through a unit of work by an exchange that the unit of work was
     * not created for (such as a part that shares the unit of work of its parent, sent to seda), so it would run with
     * the outcome of the other exchange.
     */
    COMMIT_HANDED_OVER_BY_OTHER_EXCHANGE("commit handed over by an exchange its unit of work was not created for", "R2",
                                         Fixes.BY_OTHER_EXCHANGE),

    /**
     * Rule R2: the commit of a consumer was handed over a second time from a unit of work it had already left, such as
     * by two exchanges that share the unit of work and hand over at the same time.
     */
    COMMIT_HANDED_OVER_TWICE("commit handed over twice", "R2", Fixes.HANDED_OVER_TWICE),

    /**
     * Rule R2: the commit of a consumer never ran: the exchange its unit of work was created for is done and the commit
     * was neither run, handed over nor taken out; or the CamelContext stopped while it waited in a copy that was never
     * routed (such as a copy in a seda queue that is dropped).
     */
    COMMIT_NEVER_RUN("commit never ran", "R2", Fixes.NEVER_RUN);

    private final String title;
    private final String rule;
    private final String fix;

    Violation(String title, String fix) {
        this(title, "R1", fix);
    }

    Violation(String title, String rule, String fix) {
        this.title = title;
        this.rule = rule;
        this.fix = fix;
    }

    /**
     * A short description of the violation, as in the first line of its report.
     */
    public String getTitle() {
        return title;
    }

    /**
     * The rule it breaks: {@code R1} (the callback of a step) or {@code R2} (the commit of a consumer).
     */
    public String getRule() {
        return rule;
    }

    /**
     * The usual fix, as in the last line of its report.
     */
    public String getFix() {
        return fix;
    }

    /**
     * The usual fixes of the violations of rule R2 (in a class of their own, as the constants of the enum cannot refer
     * to its own static fields).
     */
    private static final class Fixes {

        static final String RUN_TWICE = "let only the unit of work run the commit, once: do not run it yourself as"
                                        + " well, and call done of a unit of work only once";
        static final String BY_OTHER_EXCHANGE = "an exchange that uses the unit of work of another must hand over only"
                                                + " the on completions it added itself: give a part the unit of work of"
                                                + " its parent with setSharedUnitOfWork, and send a correlated copy"
                                                + " (ExchangeHelper.createCorrelatedCopy, which has no unit of work)"
                                                + " rather than Exchange.copy()";
        static final String HANDED_OVER_TWICE = "take each on completion out of the unit of work under its lock before"
                                                + " handing it over, so only one exchange gets it";
        static final String NEVER_RUN = "a commit that is in a unit of work runs when it is done: take it out only"
                                        + " with a handover of the exchange (handoverCompletions), and let the copies"
                                        + " it is handed over to be routed or hand it back";

        private Fixes() {
        }
    }
}
