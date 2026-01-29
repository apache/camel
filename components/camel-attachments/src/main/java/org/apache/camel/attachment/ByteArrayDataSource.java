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
package org.apache.camel.attachment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import jakarta.activation.DataSource;

import org.apache.camel.util.MimeTypeHelper;

/**
 * A {@link DataSource} that are in-memory using a byte array
 */
public class ByteArrayDataSource implements DataSource {

    private final String fileName;
    private final byte[] data;

    public ByteArrayDataSource(String fileName, byte[] data) {
        this.fileName = fileName;
        this.data = data;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(data);
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public String getContentType() {
        return MimeTypeHelper.probeMimeType(fileName);
    }

    @Override
    public String getName() {
        return fileName;
    }
}
