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
package org.apache.camel.spi;

/**
 * Marker interface for event handlers that react to {@link org.apache.camel.CamelContext} lifecycle phases.
 * <p/>
 * Implementations are placed in the {@link Registry} and discovered by Camel during bootstrap, then invoked as the
 * context moves through its lifecycle: initializing, initialized, starting, started, stopping, and stopped. Each phase
 * has its own sub-interface ({@link OnCamelContextInitializing}, {@link OnCamelContextInitialized},
 * {@link OnCamelContextStarting}, {@link OnCamelContextStarted}, {@link OnCamelContextStopping},
 * {@link OnCamelContextStopped}) which a handler implements to receive that specific callback; a single class may
 * implement several of them. This is a lightweight, registry-driven alternative to {@link LifecycleStrategy} for code
 * that only needs to hook into context startup or shutdown.
 * <p/>
 * See <a href="https://camel.apache.org/manual/lifecycle.html">Lifecycle</a> in the Camel user manual.
 *
 * @see   OnCamelContextInitializing
 * @see   OnCamelContextInitialized
 * @see   OnCamelContextStarting
 * @see   OnCamelContextStarted
 * @see   OnCamelContextStopping
 * @see   OnCamelContextStopped
 * @see   LifecycleStrategy
 * @since 3.4
 */
public interface OnCamelContextEvent {
}
