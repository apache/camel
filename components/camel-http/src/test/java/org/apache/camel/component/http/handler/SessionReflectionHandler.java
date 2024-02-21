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
package org.apache.camel.component.http.handler;

import java.io.OutputStream;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.camel.util.IOHelper;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Session;
import org.eclipse.jetty.util.Callback;

public class SessionReflectionHandler extends Handler.Abstract.NonBlocking {

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        Session session = request.getSession(true);
        OutputStream os = Response.asBufferedOutputStream(request, response);
        if (session != null && session.getAttribute("foo") == null) {
            session.setAttribute("foo", "bar");
            os.write("New ".getBytes());
        } else {
            os.write("Old ".getBytes());
        }
        IOHelper.copyAndCloseInput(Request.asInputStream(request), os);
        response.setStatus(HttpServletResponse.SC_OK);

        callback.succeeded();
        return true;
    }
}
