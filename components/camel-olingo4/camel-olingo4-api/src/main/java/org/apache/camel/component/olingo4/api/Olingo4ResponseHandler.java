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
package org.apache.camel.component.olingo4.api;

import java.util.Map;

/**
 * Callback interface to asynchronously process Olingo4 response.
 */
public interface Olingo4ResponseHandler<T> {

    /**
     * Handle response data on successful completion of Olingo4 request.
     * 
     * @param response response data from Olingo4, may be NULL for Olingo4
     *            operations with no response data.
     * @param responseHeaders the response HTTP headers received from the
     *            endpoint.
     */
    void onResponse(T response, Map<String, String> responseHeaders);

    /**
     * Handle exception raised from Olingo4 request.
     * 
     * @param ex exception from Olingo4 request. May be an instance of
     *            {@link org.apache.olingo.commons.api.ex.ODataException} or
     *            some other exception, such as {@link java.io.IOException}
     */
    void onException(Exception ex);

    /**
     * Handle Olingo4 request cancellation. May be caused by the underlying HTTP
     * connection being shutdown asynchronously.
     */
    void onCanceled();
}
