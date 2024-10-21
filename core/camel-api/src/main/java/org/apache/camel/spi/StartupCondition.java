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
 * Pluggable condition that must be accepted before Camel can continue starting up.
 *
 * This can be used to let Camel wait for a specific file to be present, an environment-variable, or some other custom
 * conditions.
 */
public interface StartupCondition {

    /**
     * The name of condition used for logging purposes.
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Optional logging message to log before waiting for the condition
     */
    default String getWaitMessage() {
        return null;
    }

    /**
     * Optional logging message to log if condition was not meet.
     */
    default String getFailureMessage() {
        return null;
    }

    /**
     * Checks if the condition is accepted
     *
     * @param  camelContext the Camel context (is not fully initialized)
     * @return              true to continue, false to stop and fail.
     */
    boolean canContinue(CamelContext camelContext) throws Exception;
}
