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

package org.apache.camel.component.jetty;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * To enable handle attachments with Jetty 9 when this filter is set.
 * <p/>
 * To replace the deprecated org.eclipse.jetty.servlets.MultiPartFilter Tell AttachmentHttpBinding to use Servlet 3
 * HttpServletRequest.getParts API
 */
public class MultiPartFilter implements Filter {

    public static final String MULTIPART = "populate.multipart";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest srequest = (HttpServletRequest) request;
        if (srequest.getContentType() == null || !srequest.getContentType().startsWith("multipart/form-data")) {
            chain.doFilter(request, response);
        } else {
            srequest.getParts(); //load and init attachments
            request.setAttribute(MULTIPART, Boolean.TRUE);
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {

    }

}
