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
package org.apache.camel.component.undertow;

import java.net.URI;

public class HttpHandlerRegistrationInfo {

    private final Boolean matchOnUriPrefix;
    private final String methodRestrict;
    private final URI uri;

    public HttpHandlerRegistrationInfo(URI uri, String methodRestrict, Boolean matchOnUriPrefix) {
        super();
        this.matchOnUriPrefix = matchOnUriPrefix;
        this.methodRestrict = methodRestrict;
        this.uri = uri;
    }

    public String getMethodRestrict() {
        return methodRestrict;
    }

    public URI getUri() {
        return uri;
    }

    public Boolean isMatchOnUriPrefix() {
        return matchOnUriPrefix;
    }

    @Override
    public String toString() {
        return uri + "?matchOnUriPrefix=" + matchOnUriPrefix + "&methodRestrict=" + methodRestrict;
    }

}
