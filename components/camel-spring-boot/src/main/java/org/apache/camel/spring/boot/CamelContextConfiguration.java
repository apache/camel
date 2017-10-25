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
package org.apache.camel.spring.boot;

import org.apache.camel.CamelContext;

/**
 * Callback that allows custom logic during starting up {@link CamelContext} and just after
 * {@link CamelContext} has been fully started.
 */
public interface CamelContextConfiguration {

    /**
     * Called during Spring Boot is starting up and is starting up {@link CamelContext}.
     */
    void beforeApplicationStart(CamelContext camelContext);

    /**
     * Called after Spring Boot and {@link CamelContext} has just been started up.
     * This means there Camel routes may already be active and have started processing incoming messages.
     */
    void afterApplicationStart(CamelContext camelContext);

}
