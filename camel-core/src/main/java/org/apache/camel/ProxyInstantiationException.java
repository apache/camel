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
 * Exception indicating a failure while trying to create a proxy of a given type and on a given endpoint
 *
 * @version 
 */
public class ProxyInstantiationException extends RuntimeCamelException {
    private static final long serialVersionUID = -2050115486047385506L;

    private final Class<?> type;
    private final Endpoint endpoint;

    public ProxyInstantiationException(Class<?> type, Endpoint endpoint, Throwable cause) {
        super("Could not instantiate proxy of type " + type.getName() + " on endpoint " + endpoint, cause);
        this.type = type;
        this.endpoint = endpoint;
    }

    public Class<?> getType() {
        return type;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }
}
