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

package org.apache.camel;

/**
 * Represents the history of a Camel {@link Message} how it was routed by the Camel routing engine.
 */
public interface MessageHistory {

    /**
     * Gets the route id at the point of this history.
     */
    String getRouteId();

    /**
     * Gets the node at the point of this history.
     */
    NamedNode getNode();

    /**
     * Gets the point in time the message history was created
     */
    long getTime();

    /**
     * Gets the elapsed time in millis processing the node took (this is 0 until the node processing is done)
     */
    long getElapsed();

    /**
     * The elapsed time since created.
     */
    default long getElapsedSinceCreated() {
        return System.nanoTime() - getTime();
    }

    /**
     * Used for signalling that processing of the node is done.
     */
    void nodeProcessingDone();

    /**
     * Used for signalling that processing of the node is done.
     *
     * @param delta extra time in millis that should be subtracted from the processing time
     */
    void nodeProcessingDone(long delta);

    /**
     * A read-only copy of the message at the point of this history (if this has been enabled).
     */
    Message getMessage();

    /**
     * Used specially during debugging where some EIP nodes are not accepted for debugging and are essentially skipped.
     * This allows tooling to avoid dumping message history for nodes that did not take part in the debugger.
     */
    void setAcceptDebugger(boolean acceptDebugger);

    /**
     * Used specially during debugging where some EIP nodes are not accepted for debugging and are essentially skipped.
     * This allows tooling to avoid dumping message history for nodes that did not take part in the debugger.
     */
    boolean isAcceptDebugger();

    /**
     * Used specially during debugging to know that an EIP was skipped over
     */
    void setDebugSkipOver(boolean skipOver);

    /**
     * Used specially during debugging to know that an EIP was skipped over
     */
    boolean isDebugSkipOver();
}
