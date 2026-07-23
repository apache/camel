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

/**
 * An {@link Endpoint} that delegates all operations to another underlying {@link Endpoint}, following the
 * <em>Decorator</em> pattern.
 * <p/>
 * Implementations use this interface to wrap an existing endpoint to modify, intercept, or enrich its behaviour without
 * changing the endpoint URI visible to the rest of the routing engine.
 *
 * @see Endpoint
 */
public interface DelegateEndpoint extends Endpoint {

    /**
     * Gets the delegated {@link Endpoint}.
     *
     * @return the Endpoint we delegate to
     */
    Endpoint getEndpoint();

}
