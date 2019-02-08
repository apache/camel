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
package org.apache.camel;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

/**
 * Represents the options available when shutting down routes.
 * <p/>
 * Is used for example to defer shutting down a route until all inflight exchanges have
 * been completed, after which the route can be shutdown safely.
 * <p/>
 * This allows fine grained configuration in accomplishing graceful shutdown where you
 * have for example some internal route which other routes are dependent upon.
 * <ul>
 *   <li>Default - The <b>default</b> behavior where a route will attempt to shutdown now</li>
 *   <li>Defer - Will defer shutting down the route and let it be active during graceful shutdown.
 *               The route will be shutdown at a later stage during the graceful shutdown process.</li>
 * </ul>
 */
@XmlType
@XmlEnum
public enum ShutdownRoute {

    Default, Defer

}
