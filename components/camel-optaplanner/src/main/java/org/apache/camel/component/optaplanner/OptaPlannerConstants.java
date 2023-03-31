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

import org.apache.camel.spi.Metadata;

public interface OptaPlannerConstants {
    String DEFAULT_SOLVER_ID = "DEFAULT_SOLVER";
    @Metadata(label = "producer", description = "Specifies the solverId to use.", javaType = "String")
    String SOLVER_ID = "CamelOptaPlannerSolverId";
    @Metadata(label = "producer", description = "Specify whether to use another thread for submitting Solution instances\n" +
                                                "rather than blocking the current thread.",
              javaType = "Boolean")
    String IS_ASYNC = "CamelOptaPlannerIsAsync";
    @Metadata(label = "consumer", description = "The best planning solution.", javaType = "Object")
    String BEST_SOLUTION = "CamelOptaPlannerBestSolution";
    @Metadata(label = "producer", description = "Is solving.", javaType = "Boolean")
    String IS_SOLVING = "CamelOptaPlannerIsSolving";
    @Metadata(label = "producer", description = "The Solver Manager.",
              javaType = "org.optaplanner.core.api.solver.SolverManager")
    String SOLVER_MANAGER = "CamelOptaPlannerSolverManager";
}
