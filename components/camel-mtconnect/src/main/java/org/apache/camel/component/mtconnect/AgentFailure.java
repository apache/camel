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
package org.apache.camel.component.mtconnect;

/** An agent request that did not yield a usable document, classified by how the consumer reacts to it. */
final class AgentFailure extends Exception {

    /** How the request failed. */
    enum Kind {
        /**
         * The connection failed, timed out, broke, or the agent sent nothing for the silence bound: two heartbeats when
         * streaming, the connect timeout for the polling consumer and the producer. For the consumer it is an outage.
         */
        UNREACHABLE,
        /**
         * An HTTP error status without an MTConnect error document, a body that is not one, or a sample answer that is
         * not a stream: an outage.
         */
        ERROR,
        /** An MTConnect error document other than OUT_OF_RANGE: reported, then the consumer starts over. */
        AGENT_ERROR
    }

    private final Kind kind;

    AgentFailure(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
