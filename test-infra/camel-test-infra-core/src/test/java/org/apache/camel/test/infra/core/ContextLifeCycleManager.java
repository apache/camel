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

package org.apache.camel.test.infra.core;

import org.apache.camel.CamelContext;

/**
 * A life cycle manager for the Camel instance manages how the context is started, stopped and configured based on which
 * phase of the test is being executed.
 */
public interface ContextLifeCycleManager {

    /**
     * A hook to execute after all tests are executed
     *
     * @param context the context instance
     */
    void afterAll(CamelContext context);

    /**
     * A hook to execute before all tests are executed
     *
     * @param context the context instance
     */
    void beforeAll(CamelContext context);

    /**
     * A hook to execute after each test is executed
     *
     * @param context the context instance
     */
    void afterEach(CamelContext context);

    /**
     * A hook to execute before each test is executed
     *
     * @param context the context instance
     */
    void beforeEach(CamelContext context);
}
