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

import org.apache.camel.Expression;
import org.apache.camel.Processor;

/**
 * Interface for different Throttler implementations.
 */
public interface Throttler extends Processor {

    /**
     * Whether to reject the incoming request such as when max rate limit hit.
     */
    boolean isRejectExecution();

    /**
     * Whether to delay task asynchronously
     */
    boolean isAsyncDelayed();

    /**
     * Whether to process the task using the current thread when the throttler rejected executing the task
     */
    boolean isCallerRunsWhenRejected();

    /**
     * Expression for computing the maximum requests (can be dynamic and group based)
     */
    void setMaximumRequestsExpression(Expression maxConcurrentRequestsExpression);

    /**
     * The current maximum number of requests
     */
    int getCurrentMaximumRequests();

    /**
     * The mode of the throttler (different implementations).
     */
    String getMode();
}
