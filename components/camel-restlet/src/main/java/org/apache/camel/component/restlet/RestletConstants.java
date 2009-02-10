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
package org.apache.camel.component.restlet;

/**
 * Constants for Restlet component
 * 
 * @version $Revision$
 */
public final class RestletConstants {
    
    public static final String LOGIN = "org.apache.camel.restlet.auth.login";
    public static final String PASSWORD = "org.apache.camel.restlet.auth.password";
    public static final String MEDIA_TYPE = "org.apache.camel.restlet.mediaType";
    public static final String RESPONSE_CODE = "org.apache.camel.restlet.responseCode";
    public static final String QUERY_STRING = "org.apache.camel.restlet.queryString";

    private RestletConstants() {
    }
}
