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
package org.apache.camel.maven;

import java.util.Collections;
import java.util.List;

/**
 * Represents an API to use for generating Camel Component.
 */
public class ApiProxy {
    private String apiName;
    private String proxyClass;

    private List<ApiMethodAlias> aliases = Collections.EMPTY_LIST;

    public ApiProxy() {
    }

    public ApiProxy(String apiName, String proxyClass) {
        this.apiName = apiName;
        this.proxyClass = proxyClass;
    }

    public ApiProxy(String apiName, String proxyClass, List<ApiMethodAlias> aliases) {
        this(apiName, proxyClass);
        this.aliases = aliases;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getProxyClass() {
        return proxyClass;
    }

    public void setProxyClass(String proxyClass) {
        this.proxyClass = proxyClass;
    }

    public List<ApiMethodAlias> getAliases() {
        return aliases;
    }

    public void setAliases(List<ApiMethodAlias> aliases) {
        this.aliases = aliases;
    }

    public void validate() {
        if (apiName == null || proxyClass == null) {
            throw new IllegalArgumentException("Properties apiName and proxyClass are required");
        }
    }
}
