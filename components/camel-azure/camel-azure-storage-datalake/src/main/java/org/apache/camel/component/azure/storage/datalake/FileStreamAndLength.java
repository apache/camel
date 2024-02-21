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
package org.apache.camel.component.azure.storage.datalake;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;

public final class FileStreamAndLength {
    private final InputStream inputStream;
    private final long streamLength;

    private FileStreamAndLength(InputStream inputStream, long streamLength) {
        this.inputStream = inputStream;
        this.streamLength = streamLength;
    }

    @SuppressWarnings("rawtypes")
    public static FileStreamAndLength createFileStreamAndLengthFromExchangeBody(final Exchange exchange) throws IOException {
        Object body = exchange.getIn().getBody();

        if (body instanceof WrappedFile) {
            body = ((WrappedFile) body).getFile();
        }

        if (body instanceof InputStream) {
            if (!((InputStream) body).markSupported()) {
                throw new IllegalArgumentException("Inputstream does not support mark rest operations");
            }
            return new FileStreamAndLength((InputStream) body, DataLakeUtils.getInputStreamLength((InputStream) body));
        }

        if (body instanceof File) {
            return new FileStreamAndLength(new BufferedInputStream(new FileInputStream((File) body)), ((File) body).length());
        }

        if (body instanceof byte[]) {
            return new FileStreamAndLength(new ByteArrayInputStream((byte[]) body), ((byte[]) body).length);
        }

        final InputStream inputStream
                = exchange.getContext().getTypeConverter().tryConvertTo(InputStream.class, exchange, body);

        if (inputStream == null) {
            throw new IllegalArgumentException("Unsupported file type");
        }

        return new FileStreamAndLength(inputStream, DataLakeUtils.getInputStreamLength(inputStream));
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public long getStreamLength() {
        return streamLength;
    }
}
