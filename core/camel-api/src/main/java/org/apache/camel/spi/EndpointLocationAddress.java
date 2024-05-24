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
 * Used for getting information about (hosted or external) service addresses used for connecting to a remote system such
 * as message brokers, databases, or cloud services.
 *
 * Hosted services are running inside this Camel application such as with embedded HTTP server for Rest DSL, or
 * TCP networking with netty etc.
 */
public interface EndpointLocationAddress {

    /**
     * Is this service address hosted (in this Camel application) or external such as on a remote network connection.
     */
    default boolean isHostedAddress() {
        return false;
    }

    /**
     * Gets the remote address such as URL or hostname
     *
     * @return the address or null if no address can be determined.
     */
    String getAddress();

    /**
     * Optional additional metadata that is relevant to the address as key value pairs.
     * Notice that the metadata is not supposed to contain sensitive security details
     * such as access token, api keys, or passwords. Only share information that can
     * be safely accessed and written to logs.
     *
     * @return optional metadata or null if no data
     */
    default Map<String, String> getAddressMetadata() {
        return null;
    }
}
