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

import java.util.Map;

/**
 * Various statistics about endpoint utilization, such as from EIP patterns that uses dynamic endpoints.
 */
public interface EndpointUtilizationStatistics {

    /**
     * Maximum number of elements that we can have information about
     */
    int maxCapacity();

    /**
     * Current number of endpoints we have information about
     */
    int size();

    /**
     * Callback when an endpoint is being utilizated by an {@link org.apache.camel.Processor} EIP
     * such as sending a message to a dynamic endpoint.
     *
     * @param uri  the endpoint uri
     */
    void onHit(String uri);

    /**
     * To remove an endpoint from tracking information about its utilization
     *
     * @param uri  the endpoint uri
     */
    void remove(String uri);

    /**
     * Gets the endpoint utilization statistics.
     *
     * @return a map with uri and number of usage of the endpoint.
     */
    Map<String, Long> getStatistics();

    /**
     * Clears all information.
     */
    void clear();

}
