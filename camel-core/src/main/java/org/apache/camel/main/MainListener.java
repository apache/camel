/**
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
package org.apache.camel.main;

import org.apache.camel.CamelContext;

/**
 * A lifecycle listener to receive callbacks when the main is started and stopped.
 */
public interface MainListener {

    /**
     * Callback before the CamelContext(s) is being created and started.
     *
     * @param main  the main instance
     */
    void beforeStart(MainSupport main);

    /**
     * Callback to configure <b>each</b> created CamelContext.
     * <p/>
     * Notice this callback will be invoked for <b>each</b> CamelContext and therefore can be invoked
     * multiple times if there is 2 or more CamelContext's being created.
     *
     * @param context the created CamelContext
     */
    void configure(CamelContext context);

    /**
     * Callback after the CamelContext(s) has been started.
     *
     * @param main  the main instance
     */
    void afterStart(MainSupport main);

    /**
     * Callback before the CamelContext(s) is being stopped.
     *
     * @param main  the main instance
     */
    void beforeStop(MainSupport main);

    /**
     * Callback after the CamelContext(s) has been stopped.
     *
     * @param main  the main instance
     */
    void afterStop(MainSupport main);
}
