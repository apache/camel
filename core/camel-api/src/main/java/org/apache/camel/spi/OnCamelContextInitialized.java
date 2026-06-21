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

import org.apache.camel.CamelContext;

/**
 * Event handler invoked after the {@link CamelContext} has been initialized, before it is started.
 * <p/>
 * Implementations are registered in the {@link Registry} and discovered by Camel during bootstrap. At this point all
 * routes and services have been initialized but not yet started, so this phase suits inspection or final adjustments
 * that depend on a fully initialized context. For the full set of lifecycle callbacks see {@link OnCamelContextEvent}.
 * <p/>
 * See <a href="https://camel.apache.org/manual/lifecycle.html">Lifecycle</a> in the Camel user manual.
 *
 * @see   OnCamelContextEvent
 * @since 3.4
 */
@FunctionalInterface
public interface OnCamelContextInitialized extends OnCamelContextEvent {

    /**
     * Callback invoked after the {@link CamelContext} has been initialized.
     *
     * @param context the camel context
     */
    void onContextInitialized(CamelContext context);
}
