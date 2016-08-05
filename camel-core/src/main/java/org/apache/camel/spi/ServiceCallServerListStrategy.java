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
package org.apache.camel.spi;

import java.util.List;

/**
 * Allows SPIs to implement custom server list strategies for the Service Call EIP.
 *
 * @see ServiceCallLoadBalancer
 * @see ServiceCallServer
 */
public interface ServiceCallServerListStrategy<T extends ServiceCallServer> {

    /**
     * Gets the initial list of servers.
     * <p/>
     * This method may return <tt>null</tt> or an empty list.
     *
     * @param name the service name
     */
    List<T> getInitialListOfServers(String name);

    /**
     * Gets the updated list of servers.
     * <p/>
     * This method can either be called on-demand prior to a service call, or have
     * a background job that is scheduled to update the list, or a watcher
     * that triggers when the list of servers changes.
     *
     * @param name the service name
     */
    List<T> getUpdatedListOfServers(String name);

}
