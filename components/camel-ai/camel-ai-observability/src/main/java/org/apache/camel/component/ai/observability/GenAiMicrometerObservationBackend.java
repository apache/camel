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
package org.apache.camel.component.ai.observability;

import java.util.Map;

/**
 * Optional Micrometer Observation backend for GenAI operations. Implementations must not expose Micrometer Observation
 * types on this interface so {@link GenAiObservabilityImpl} can load without micrometer-observation on the classpath.
 */
interface GenAiMicrometerObservationBackend {

    boolean isAvailable();

    /**
     * Starts a Micrometer Observation for the GenAI client call, nested under {@code parent} when not {@code null}.
     * Returns {@code null} when the registry produces a no-op Observation.
     */
    Handle start(GenAiObservationContext context, Handle parent);

    interface Handle {

        /**
         * Stops the Observation, adding the attributes collected for the call as high-cardinality key values (they
         * reach the trace, never the metric tags).
         */
        void stop(Map<String, Object> attributes, Throwable error);
    }
}
