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
 * Thrown if Camel failed to create a consumer for a given endpoint.
 *
 * @version 
 */
public class FailedToCreateConsumerException extends RuntimeCamelException {
    private static final long serialVersionUID = 1916718168052020246L;

    private final String uri;

    public FailedToCreateConsumerException(String endpointURI, Throwable cause) {
        super("Failed to create Consumer for endpoint for: " + endpointURI + ". Reason: " + cause, cause);
        this.uri = endpointURI;
    }

    public FailedToCreateConsumerException(Endpoint endpoint, Throwable cause) {
        super("Failed to create Consumer for endpoint: " + endpoint + ". Reason: " + cause, cause);
        this.uri = endpoint.getEndpointUri();
    }

    public FailedToCreateConsumerException(Endpoint endpoint, String message, Throwable cause) {
        super("Failed to create Consumer for endpoint: " + endpoint + ". Reason: " + message, cause);
        this.uri = endpoint.getEndpointUri();
    }

    public FailedToCreateConsumerException(Endpoint endpoint, String message) {
        super("Failed to create Consumer for endpoint: " + endpoint + ". Reason: " + message);
        this.uri = endpoint.getEndpointUri();
    }

    public String getUri() {
        return uri;
    }
}
