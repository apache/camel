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
package org.apache.camel.component.netty4.http;

public interface SecurityConstraint {

    /**
     * Performs a security restricted check for the given web resource.
     * <p/>
     * The returned value indicates which roles the user must be in to access the restricted resource.
     *
     * @param url   the web resource
     * @return <tt>null</tt> if not restricted, otherwise <tt>*</tt> (wildcard) matches any roles, otherwise a comma separated String with roles
     */
    String restricted(String url);

}
