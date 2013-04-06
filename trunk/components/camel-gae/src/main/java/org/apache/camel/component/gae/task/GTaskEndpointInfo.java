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
package org.apache.camel.component.gae.task;

import java.net.URI;

import org.apache.camel.util.UnsafeUriCharactersEncoder;

/**
 * Encapsulates the required canonicalization logic for <code>gtask</code>
 * endpoint URIs.
 */
class GTaskEndpointInfo {

    private static final String SLASH = "/";
    private static final String COLON = ":";
    
    private String uri;
    private String uriPath;
    private URI uriObject;
    
    public GTaskEndpointInfo(String uri, String uriPath) throws Exception {
        this.uri = uri;
        this.uriPath = uriPath;
        this.uriObject = new URI(UnsafeUriCharactersEncoder.encode(uri));
    }
    
    public String getUri() {
        return uri;
    }

    public String getUriPath() {
        return uriPath;
    }

    public String getCanonicalUri() {
        return uriObject.getScheme() + COLON + getCanonicalUriPath();
    }
    
    public String getCanonicalUriPath() {
        if (uriPath.startsWith(SLASH)) {
            return uriPath;
        }
        return SLASH + uriPath;
    }
    
}
