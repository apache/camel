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
package org.apache.camel.component.gae.http;

import java.io.IOException;
import java.util.Enumeration;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.util.IOHelper;

public class GHttpTestServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Set default response code
        resp.setStatus(200);
        // Copy headers from request to response
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            Enumeration<String> headers = req.getHeaders(headerName);
            while (headers.hasMoreElements()) {
                String headerValue = headers.nextElement();
                resp.addHeader(headerName, headerValue);
                // Set custom response code
                // if requested by client
                if (headerName.equals("code")) {
                    resp.setStatus(Integer.parseInt(headerValue));
                }
            }
        }

        // add some special response headers
        resp.addHeader("testUrl", req.getRequestURL().toString());
        resp.addHeader("testMethod", req.getMethod().toString());
        if (req.getQueryString() != null) {
            resp.addHeader("testQuery", req.getQueryString());            
        }

        // Copy body from request to response
        IOHelper.copyAndCloseInput(req.getInputStream(), resp.getOutputStream());
        resp.getWriter().flush();
    }
}
