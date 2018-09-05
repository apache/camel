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
package org.apache.camel.component.http.handler;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 *
 */
public class BasicValidationHandler extends AbstractHandler {

    protected String expectedMethod;
    protected String expectedQuery;
    protected Object expectedContent;
    protected String responseContent;

    public BasicValidationHandler(String expectedMethod, String expectedQuery,
                                  Object expectedContent, String responseContent) {
        this.expectedMethod = expectedMethod;
        this.expectedQuery = expectedQuery;
        this.expectedContent = expectedContent;
        this.responseContent = responseContent;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        baseRequest.setHandled(true);

        if (expectedMethod != null && !expectedMethod.equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if (!validateQuery(request)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (expectedContent != null) {
            StringBuilder content = new StringBuilder();
            String line = null;
            while ((line = request.getReader().readLine()) != null) {
                content.append(line);
            }

            if (!expectedContent.equals(content.toString())) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
        }

        response.setStatus(HttpServletResponse.SC_OK);
        String content = buildResponse(request);
        if (content != null) {
            response.setContentType("text/plain; charset=utf-8");
            PrintWriter out = response.getWriter();
            out.print(content);
        }
    }

    protected String buildResponse(HttpServletRequest request) {
        return responseContent;
    }

    protected boolean validateQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        if (expectedQuery != null && !expectedQuery.equals(query)) {
            return false;
        }
        return true;
    }

}
