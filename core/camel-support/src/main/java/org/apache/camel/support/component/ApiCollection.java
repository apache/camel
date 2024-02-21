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
package org.apache.camel.support.component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base class for a collection of ApiMethods. Meant to be extended by Components to create the api name map.
 */
public abstract class ApiCollection<E extends Enum<E> & ApiName, T> {

    private Map<E, ApiMethodHelper<? extends ApiMethod>> apiHelpers = Collections.emptyMap();
    private Map<Class<? extends ApiMethod>, E> apiMethods = Collections.emptyMap();
    private Set<String> apiNames = Collections.emptySet();

    public final Map<E, ApiMethodHelper<? extends ApiMethod>> getApiHelpers() {
        return apiHelpers;
    }

    public final Map<Class<? extends ApiMethod>, E> getApiMethods() {
        return apiMethods;
    }

    /**
     * Returns a {@link ApiMethodHelper} for a particular API.
     *
     * @param  apiName name of the API
     * @return         helper class to work with {@link ApiMethod}
     */
    public final ApiMethodHelper<? extends ApiMethod> getHelper(E apiName) {
        return apiHelpers.get(apiName);
    }

    /**
     * Returns a list of API name strings.
     *
     * @return list of API names.
     */
    public final Set<String> getApiNames() {
        return apiNames;
    }

    public final E getApiName(Class<? extends ApiMethod> apiMethod) {
        return apiMethods.get(apiMethod);
    }

    /**
     * Creates an endpoint configuration for a particular API
     *
     * @param  apiName name of the API.
     * @return         Endpoint configuration object for the API.
     */
    public abstract T getEndpointConfiguration(E apiName);

    protected final void setApiHelpers(Map<E, ApiMethodHelper<? extends ApiMethod>> apiHelpers) {
        this.apiHelpers = Collections.unmodifiableMap(apiHelpers);

        this.apiNames = apiHelpers.keySet()
                .stream()
                .map(ApiName::getName).collect(Collectors.toUnmodifiableSet());
    }

    protected final void setApiMethods(Map<Class<? extends ApiMethod>, E> apiMethods) {
        this.apiMethods = Collections.unmodifiableMap(apiMethods);
    }
}
