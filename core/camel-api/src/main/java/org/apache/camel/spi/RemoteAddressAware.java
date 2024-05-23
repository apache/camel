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

import java.util.Map;

/**
 * Used for getting information about remote URL used for connecting to a remote system,
 * such as from an {@link org.apache.camel.Endpoint} or {@link org.apache.camel.Component}
 * that connects to messaging brokers, cloud systems, databases etc.
 */
public interface RemoteAddressAware {

    /**
     * Gets the remote address such as URL or hostname
     *
     * @return the address or null if no address can be resolved
     */
    String getAddress();

    /**
     * Optional additional metadata that is relevant to the remote address as key value pairs
     *
     * @return optional metadata or null if no data
     */
    default Map<String, String> getAddressMetadata() {
        return null;
    }
}
