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

package org.apache.camel.resume;

import java.util.function.Predicate;

/**
 * An interface for listening to consumer events and allow proxying between a consumer predicate and the Camel
 * component. The whole of the consumer predicate is that of evaluating whether the consumption (from the internal Camel
 * consumer) can continue or should be put on pause.
 */
public interface ConsumerListener<C, P> {

    /**
     * This sets the predicate responsible for evaluating whether the processing can resume or not. Such predicate
     * should return true if the consumption can resume, or false otherwise. The exact point of when the predicate is
     * called is dependent on the component, and it may be called on either one of the available events. Implementations
     * should not assume the predicate to be called at any specific point.
     */
    void setResumableCheck(Predicate<?> afterConsumeEval);

    /**
     * This is an event that runs after data consumption.
     *
     * @param  consumePayload the resume payload if any
     * @return                true if the consumer should processing or false otherwise.
     */
    boolean afterConsume(C consumePayload);

    /**
     * This is an event that runs after data processing.
     *
     * @param  processingPayload the resume payload if any
     * @return                   true if the consumer should continue or false otherwise.
     */
    boolean afterProcess(P processingPayload);
}
