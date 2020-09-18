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
package org.apache.camel.component.optaplanner;

import java.util.Set;

public class OptaplannerEventSupport {

    private OptaPlannerEndpoint optaPlannerEndpoint;
    private Long problemId;

    public OptaplannerEventSupport(OptaPlannerEndpoint optaPlannerEndpoint, Long problemId) {
        this.optaPlannerEndpoint = optaPlannerEndpoint;
        this.problemId = problemId;
    }

    protected void updateBestSolution(Object newBestSolution) {
        OptaplannerSolutionEvent event = new OptaplannerSolutionEvent(newBestSolution);
        Set<OptaplannerSolutionEventListener> listeners = optaPlannerEndpoint.getSolutionEventListeners(problemId);
        if (listeners != null) {
            listeners.stream().forEachOrdered(l -> l.bestSolutionChanged(event));
        }
    }
}
