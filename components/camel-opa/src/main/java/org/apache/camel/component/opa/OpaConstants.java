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
package org.apache.camel.component.opa;

import org.apache.camel.spi.Metadata;

public final class OpaConstants {
    private static final String HEADER_PREFIX = "CamelOpa";

    @Metadata(label = "producer",
              description = "The allow/deny verdict of the policy evaluation. Always overwritten by the component,"
                            + " so a value set by an inbound message never survives into the route.",
              javaType = "Boolean")
    public static final String DECISION_ALLOW = HEADER_PREFIX + "DecisionAllow";

    @Metadata(label = "producer",
              description = "The raw decision document returned by OPA. Useful for policies that return more than a"
                            + " boolean, such as obligations, row filters or deny reasons.",
              javaType = "Object")
    public static final String DECISION = HEADER_PREFIX + "Decision";

    @Metadata(label = "producer",
              description = "The policy path that was evaluated. Set by the component for observability; it is not"
                            + " read as an input and cannot be used to select a different policy.",
              javaType = "String")
    public static final String POLICY_PATH = HEADER_PREFIX + "PolicyPath";

    private OpaConstants() {
    }
}
