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
package org.apache.camel.component.resteasy.servlet;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Custom HttpServletRequestWrapper used for creating request with cached body for better manipulation in
 * ResteasyCamelServlet (ResteasyConsumer).
 */
public class ResteasyHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private ServletInputStream inputStream;
    private BufferedReader reader;
    private ResteasyServletInputStreamCopier copier;

    public ResteasyHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (inputStream == null) {
            inputStream = getRequest().getInputStream();
            copier = new ResteasyServletInputStreamCopier(inputStream);
        }
        return copier;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (inputStream != null) {
            throw new IllegalStateException("getInputStream() has already been called on this response.");
        }
        if (reader == null) {
            reader = new BufferedReader(new InputStreamReader(copier, getRequest().getCharacterEncoding()));
        }
        return reader;
    }

    public byte[] getCopy() {
        if (copier != null) {
            return copier.getCopy();
        } else {
            return new byte[0];
        }
    }

    public ByteArrayOutputStream getStream() {
        if (copier != null) {
            return copier.getStream();
        } else {
            return null;
        }
    }
}
