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
package org.apache.camel.component.servletlistener;

import org.apache.camel.util.jndi.JndiContext;

/**
 * A callback lifecycle allows end users to implement custom logic before
 * the {@link ServletCamelContext} is started and stopped.
 */
public interface CamelContextLifecycle {

    /**
     * Callback before starting {@link ServletCamelContext}.
     *
     * @param camelContext the Camel context
     * @param jndi         the JNDI context.
     * @throws Exception is thrown if any error.
     */
    void beforeStart(ServletCamelContext camelContext, JndiContext jndi) throws Exception;

    /**
     * Callback after {@link ServletCamelContext} has been started.
     *
     * @param camelContext the Camel context
     * @param jndi         the JNDI context.
     * @throws Exception is thrown if any error.
     */
    void afterStart(ServletCamelContext camelContext, JndiContext jndi) throws Exception;

    /**
     * Callback before stopping {@link ServletCamelContext}.
     *
     * @param camelContext the Camel context
     * @param jndi         the JNDI context.
     * @throws Exception is thrown if any error.
     */
    void beforeStop(ServletCamelContext camelContext, JndiContext jndi) throws Exception;

    /**
     * Callback after {@link ServletCamelContext} has been stopped.
     *
     * @param camelContext the Camel context
     * @param jndi         the JNDI context.
     * @throws Exception is thrown if any error.
     */
    void afterStop(ServletCamelContext camelContext, JndiContext jndi) throws Exception;

}
