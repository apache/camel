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
package org.apache.camel.util.component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Base class for a collection of ApiMethods. Meant to be extended by Components to create the api name map.
 */
public abstract class ApiCollection<E extends Enum<E> & ApiName, T> {

    protected final Map<E, ApiMethodHelper<? extends ApiMethod>> apis = new HashMap<E, ApiMethodHelper<? extends ApiMethod>>();
    protected final HashMap<Class<? extends ApiMethod>, E> apiMethods = new HashMap<Class<? extends ApiMethod>, E>();

    public final Map<E, ApiMethodHelper<? extends ApiMethod>> getApiHelpers() {
        return Collections.unmodifiableMap(apis);
    }

    public final Map<Class<? extends ApiMethod>, E> getApiMethods() {
        return Collections.unmodifiableMap(apiMethods);
    }

    /**
     * Returns a {@link ApiMethodHelper} for a particular API.
     * @param apiName name of the API
     * @return helper class to work with {@link ApiMethod}
     */
    public final ApiMethodHelper<? extends ApiMethod> getHelper(E apiName) {
        return apis.get(apiName);
    }

    /**
     * Returns a list of API name strings.
     * @return list of API names.
     */
    public final Set<String> getApiNames() {
        final Set<String> result = new HashSet<String>();
        for (E api : apis.keySet()) {
            result.add(api.getName());
        }
        return Collections.unmodifiableSet(result);
    }

    public final E getApiName(Class<? extends ApiMethod> apiMethod) {
        return apiMethods.get(apiMethod);
    }

    /**
     * Creates an endpoint configuration for a particular API
     * @param apiName name of the API.
     * @return Endpoint configuration object for the API.
     */
    public abstract T getEndpointConfiguration(E apiName);
}
