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

/**
 * Thrown if Camel failed to create a producer for a given endpoint.
 *
 * @version 
 */
public class FailedToCreateProducerException extends RuntimeCamelException {
    private static final long serialVersionUID = 1341435621084082033L;

    private final String uri;

    public FailedToCreateProducerException(Endpoint endpoint, Throwable cause) {
        super("Failed to create Producer for endpoint: " + endpoint + ". Reason: " + cause, cause);
        this.uri = endpoint.getEndpointUri();
    }

    public String getUri() {
        return uri;
    }
}
