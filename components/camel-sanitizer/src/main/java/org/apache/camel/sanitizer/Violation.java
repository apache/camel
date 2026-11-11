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
 * The kinds of violation the exchange sanitizer reports. Each is a break of the contract of
 * {@link org.apache.camel.AsyncProcessor#process(org.apache.camel.Exchange, org.apache.camel.AsyncCallback)} by the
 * processor of a step of a route (rule R1: the callback of a step is called exactly once, and {@code process} does not
 * throw).
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
                                                                + " the exception, set it on the exchange and call the callback once");

    private final String title;
    private final String fix;

    Violation(String title, String fix) {
        this.title = title;
        this.fix = fix;
    }

    /**
     * A short description of the violation, as in the first line of its report.
     */
    public String getTitle() {
        return title;
    }

    /**
     * The usual fix, as in the last line of its report.
     */
    public String getFix() {
        return fix;
    }
}
