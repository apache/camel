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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

import org.apache.camel.converter.stream.CachedByteArrayOutputStream;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.commons.io.IOUtils;

/**
 * Class for copying input stream from HttpRequest
 */
public class ResteasyServletInputStreamCopier extends ServletInputStream {

    private InputStreamCache input;
    private CachedByteArrayOutputStream copy;

    public ResteasyServletInputStreamCopier(InputStream inputStream) throws IOException {
        /* create a new input stream from the cached request body */
        byte[] inputBytes = IOUtils.toByteArray(inputStream);
        this.input = new InputStreamCache(inputBytes);
        this.copy = new CachedByteArrayOutputStream(inputBytes.length);
    }

    @Override
    public int read() throws IOException {
        int i = input.read();
        if (i > 0) {
            copy.write(i);
        }
        return i;

    }

    public byte[] getCopy() {
        return copy.toByteArray();
    }

    public ByteArrayOutputStream getStream() {
        return copy;
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
    }
}
