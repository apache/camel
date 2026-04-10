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
package org.apache.camel.telemetry;

/**
 * Span kind constants for telemetry tracing.
 * <p>
 * These values describe the relationship between the span and its parent:
 * <ul>
 * <li>CLIENT - The span covers a client-side call to a remote service</li>
 * <li>SERVER - The span covers server-side handling of a remote request</li>
 * <li>PRODUCER - The span covers the production of a message to a remote system (e.g., message broker, queue, HTTP
 * endpoint)</li>
 * <li>CONSUMER - The span covers the consumption of a message from a remote system (e.g., message broker, queue, HTTP
 * endpoint)</li>
 * <li>INTERNAL - The span represents internal operations with no remote interaction</li>
 * </ul>
 */
public enum SpanKind {
    CLIENT,
    SERVER,
    PRODUCER,
    CONSUMER,
    INTERNAL
}
