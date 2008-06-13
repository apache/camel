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
package org.apache.camel.component.http;

import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import org.apache.camel.Converter;

/**
 * Some converter methods making it easy to convert the body of a message to servlet types or to switch between
 * the underlying {@link ServletInputStream} or {@link BufferedReader} payloads etc.
 *
 * @version $Revision$
 */
@Converter
public class HttpConverter {

    @Converter
    public HttpServletRequest toServletRequest(HttpMessage message) {
        if (message == null) {
            return null;
        }
        return message.getRequest();
    }

    @Converter
    public ServletInputStream toServletInputStream(HttpMessage message) throws IOException {
        HttpServletRequest request = toServletRequest(message);
        if (request != null) {
            return request.getInputStream();
        }
        return null;
    }

    @Converter
    public BufferedReader toReader(HttpMessage message) throws IOException {
        HttpServletRequest request = toServletRequest(message);
        if (request != null) {
            return request.getReader();
        }
        return null;
    }

}
