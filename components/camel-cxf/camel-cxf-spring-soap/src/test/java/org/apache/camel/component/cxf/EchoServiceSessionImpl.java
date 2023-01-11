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
package org.apache.camel.component.cxf;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import jakarta.xml.ws.WebServiceContext;
import jakarta.xml.ws.WebServiceException;
import jakarta.xml.ws.handler.MessageContext;

public class EchoServiceSessionImpl implements EchoService {

    @Resource
    private WebServiceContext context;

    @Override
    public String echo(String text) {
        // Find the HttpSession
        MessageContext mc = context.getMessageContext();
        HttpSession session = ((jakarta.servlet.http.HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST)).getSession();
        if (session == null) {
            throw new WebServiceException("No HTTP Session found");
        }
        if (session.getAttribute("foo") == null) {
            session.setAttribute("foo", "bar");
            return "New " + text;
        }
        return "Old " + text;
    }
}
