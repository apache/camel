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
 * Marker interface indicating that an {@link Endpoint} belongs to an API-based component, where the endpoint URI
 * encodes an API name and method name rather than a plain scheme and path.
 * <p/>
 * API-based components (for example camel-box) are typically generated from an API specification and expose many
 * operations as distinct endpoint URIs. This interface allows tooling and the Camel catalog to identify and document
 * them as API endpoints.
 *
 * @see   Endpoint
 * @since 3.6
 */
public interface ApiEndpoint extends Endpoint {

}
