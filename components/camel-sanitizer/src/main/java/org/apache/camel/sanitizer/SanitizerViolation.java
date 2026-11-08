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
 * One violation reported by the exchange sanitizer: its kind, where it happened, and the full text of its report (with
 * the threads and stacks). It holds ids, names and text only, never the exchange, its body or its headers.
 *
 * @since 4.24
 */
public final class SanitizerViolation {

    private final Violation violation;
    private final String camelContextName;
    private final String exchangeId;
    private final String routeId;
    private final String stepId;
    private final String processor;
    private final String text;

    SanitizerViolation(Violation violation, String camelContextName, String exchangeId, String routeId, String stepId,
                       String processor, String text) {
        this.violation = violation;
        this.camelContextName = camelContextName;
        this.exchangeId = exchangeId;
        this.routeId = routeId;
        this.stepId = stepId;
        this.processor = processor;
        this.text = text;
    }

    /**
     * The kind of violation.
     */
    public Violation getKind() {
        return violation;
    }

    /**
     * The name of the CamelContext.
     */
    public String getCamelContextName() {
        return camelContextName;
    }

    /**
     * The id of the exchange.
     */
    public String getExchangeId() {
        return exchangeId;
    }

    /**
     * The id of the route of the step.
     */
    public String getRouteId() {
        return routeId;
    }

    /**
     * The id of the step, such as {@code process1} or {@code to2}.
     */
    public String getStepId() {
        return stepId;
    }

    /**
     * The class name of the processor of the step (looking through the delegates that Camel puts around a processor),
     * or {@code null} if it could not be found.
     */
    public String getProcessor() {
        return processor;
    }

    /**
     * The full text of the report, with the threads and the stacks.
     */
    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return text;
    }
}
