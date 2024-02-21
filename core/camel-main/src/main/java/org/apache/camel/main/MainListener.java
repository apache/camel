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
package org.apache.camel.main;

/**
 * A lifecycle listener to receive callbacks when the Main is started and stopped.
 */
public interface MainListener {

    /**
     * Callback invoked after the CamelContext has been created and before the auto-configured step starts.
     *
     * @param main the main instance
     */
    void beforeInitialize(BaseMainSupport main);

    /**
     * Callback invoked after the CamelContext has been created and before the auto-configured step starts.
     *
     * @param main the main instance
     */
    void beforeConfigure(BaseMainSupport main);

    /**
     * Callback to configure the created CamelContext.
     *
     * @param main the main instance
     */
    void afterConfigure(BaseMainSupport main);

    /**
     * Callback before the CamelContext is being created and started.
     *
     * @param main the main instance
     */
    void beforeStart(BaseMainSupport main);

    /**
     * Callback after the CamelContext has been started.
     *
     * @param main the main instance
     */
    void afterStart(BaseMainSupport main);

    /**
     * Callback before the CamelContext is being stopped.
     *
     * @param main the main instance
     */
    void beforeStop(BaseMainSupport main);

    /**
     * Callback after the CamelContext has been stopped.
     *
     * @param main the main instance
     */
    void afterStop(BaseMainSupport main);
}
