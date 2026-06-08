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
package org.apache.camel;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Per-route policy controlling whether the route stops immediately or defers during
 * <a href="https://camel.apache.org/manual/graceful-shutdown.html">graceful shutdown</a>.
 * <p/>
 * Set on a route via the DSL {@code .shutdownRoute(ShutdownRoute.Defer)} call. Deferring is useful for shared internal
 * routes (for example a {@code direct:} or {@code seda:} route used by many other routes) that must remain active until
 * all dependent routes have stopped processing their in-flight exchanges. The
 * {@link org.apache.camel.spi.ShutdownStrategy} honours the deferred routes by stopping them last.
 * <ul>
 * <li>{@link #Default} - attempt to stop the route as soon as the shutdown sequence reaches it.</li>
 * <li>{@link #Defer} - keep the route running and stop it only after all {@code Default} routes have stopped.</li>
 * </ul>
 *
 * @see ShutdownRunningTask
 * @see org.apache.camel.spi.ShutdownStrategy
 */
@XmlType
@XmlEnum
public enum ShutdownRoute {

    Default,
    Defer

}
