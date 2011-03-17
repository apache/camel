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
 * A runtime exception thrown if a routing processor such as a
 * {@link org.apache.camel.processor.RecipientList RecipientList} is unable to resolve an
 * {@link Endpoint} from a URI.
 *
 * @version 
 */
public class NoSuchEndpointException extends RuntimeCamelException {
    private static final long serialVersionUID = -8721487431101572630L;
    private final String uri;

    public NoSuchEndpointException(String uri) {
        super("No endpoint could be found for: " + uri
              + ", please check your classpath contains the needed Camel component jar.");
        this.uri = uri;
    }
    
    public NoSuchEndpointException(String uri, String resolveMethod) {
        super("No endpoint could be found for: " + uri
              + ", please " + resolveMethod);
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }
}
