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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * The camel filter wrapper that processes only initially dispatched requests.
 * Re-dispatched requests are ignored.
 */
public class CamelFilterWrapper implements Filter {
    
    private Filter wrapped;

    public CamelFilterWrapper(Filter wrapped) {
        this.wrapped = wrapped;
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request.getAttribute(CamelContinuationServlet.EXCHANGE_ATTRIBUTE_NAME) == null) {
            wrapped.doFilter(request, response, chain);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        wrapped.destroy();
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
        Object o = config.getServletContext().getAttribute("javax.servlet.context.tempdir");
        if (o == null) {
            //when run in embedded mode, Jetty 8 will forget to set this property,
            //but the MultiPartFilter requires it (will NPE if not set) so we'll 
            //go ahead and set it to the default tmp dir on the system.
            try {
                File file = Files.createTempFile("camel", "").toFile();
                file.delete();
                config.getServletContext().setAttribute("javax.servlet.context.tempdir",
                                                        file.getParentFile());
            } catch (IOException e) {
                //ignore
            }
        }
        wrapped.init(config);
    }

}
