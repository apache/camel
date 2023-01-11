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
import java.io.OutputStream;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;

import org.apache.camel.converter.stream.CachedByteArrayOutputStream;

/**
 * Class for copying output stream from HttpResponse
 */
public class ResteasyServletOutputStreamCopier extends ServletOutputStream {

    private static final int RESTEASY_DEFAULT_CACHED_OUTPUT_STREAM_INITIAL_SIZE = 1024; // use this value rather than ByteArrayOutputStream's default 32

    private OutputStream outputStream;
    private CachedByteArrayOutputStream copy;

    public ResteasyServletOutputStreamCopier(OutputStream outputStream) {
        this.outputStream = outputStream;
        this.copy = new CachedByteArrayOutputStream(RESTEASY_DEFAULT_CACHED_OUTPUT_STREAM_INITIAL_SIZE);
    }

    @Override
    public void write(int b) throws IOException {
        outputStream.write(b);
        copy.write(b);
    }

    public byte[] getCopy() {
        return copy.toByteArray();
    }

    public ByteArrayOutputStream getStream() {
        return copy;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
    }

}
