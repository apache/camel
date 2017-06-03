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
package org.apache.camel.http.common;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * Special adapter when {@link org.apache.camel.http.common.HttpServletUrlRewrite} is in use,
 * and the route started from came-jetty/camel-serlvet.
 * <p/>
 * This adapter ensures that we can control the context-path returned from the
 * {@link javax.servlet.http.HttpServletRequest#getContextPath()} method.
 * This allows us to ensure the context-path is based on the endpoint path, as the
 * camel-jetty/camel-servlet server implementation uses the root ("/") context-path
 * for all the servlets/endpoints.
 */
public final class UrlRewriteHttpServletRequestAdapter extends HttpServletRequestWrapper {

    private final String contextPath;

    /**
     * Creates this adapter
     * @param delegate    the real http servlet request to delegate.
     * @param contextPath use to override and return this context-path
     */
    public UrlRewriteHttpServletRequestAdapter(HttpServletRequest delegate, String contextPath) {
        super(delegate);
        this.contextPath = contextPath;
    }

    public String getContextPath() {
        return contextPath != null ? contextPath : super.getContextPath();
    }
}
