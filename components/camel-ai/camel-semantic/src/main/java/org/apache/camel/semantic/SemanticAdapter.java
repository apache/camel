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
package org.apache.camel.semantic;

/**
 * Provider-independent semantic evaluation. Implementations must support concurrent calls, bound evaluation time and
 * resource use, honor interruption, and release outstanding work on shutdown. Exceptions must not expose state or
 * credentials. Validation must not perform inference. Provider configuration belongs to the provider component.
 *
 * Adapters created by the language receive the Camel context when CamelContextAware and are managed when Service.
 * Referenced registry beans retain their existing lifecycle owner.
 */
public interface SemanticAdapter {
    /** Reject unsupported question kinds, criteria, decision policies or input selectors before traffic starts. */
    void validate(SemanticQuestion question);

    /** Synchronous, potentially blocking evaluation. Operational errors must be thrown, never returned as decisions. */
    SemanticResult evaluate(SemanticQuestion question, Object state) throws Exception;
}
