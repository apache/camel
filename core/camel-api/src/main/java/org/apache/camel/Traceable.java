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
 * Implemented by {@link Processor}s that want to expose a human-readable label used by the Camel tracing infrastructure
 * to identify them in trace output and log messages.
 * <p/>
 * When the backlog tracer or message-history feature is active, Camel calls {@link #getTraceLabel()} on each processor
 * to build a concise, path-style identifier such as {@code log:myLogger} or {@code to:direct:next}. The label is also
 * shown in the startup route summary and in JMX management output.
 *
 * @see Processor
 */
public interface Traceable {

    /**
     * Gets the trace label used for logging when tracing is enabled.
     * <p/>
     * The label should be short and precise.
     *
     * @return the label
     */
    String getTraceLabel();

}
