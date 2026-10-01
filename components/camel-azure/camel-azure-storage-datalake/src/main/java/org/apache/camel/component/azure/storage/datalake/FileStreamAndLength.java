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
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.PayloadHelper;

public final class FileStreamAndLength {
    private final InputStream inputStream;
    private final long streamLength;

    private FileStreamAndLength(InputStream inputStream, long streamLength) {
        this.inputStream = inputStream;
        this.streamLength = streamLength;
    }

    public static FileStreamAndLength createFileStreamAndLengthFromExchangeBody(final Exchange exchange) throws IOException {
        final Message message = exchange.getIn();
        Object body = message.getBody();

        if (body instanceof WrappedFile<?> wf && wf.getFile() instanceof File file) {
            body = file;
        }
        if (body instanceof File file) {
            return new FileStreamAndLength(new BufferedInputStream(new FileInputStream(file)), file.length());
        }
        if (body instanceof byte[] bytes) {
            return new FileStreamAndLength(new ByteArrayInputStream(bytes), bytes.length);
        }

        // the length of a wrapped file (such as a remote file from SFTP) is known without reading it
        long length = PayloadHelper.getBodyLength(message);

        InputStream is = body instanceof InputStream inputStream
                ? inputStream
                : exchange.getContext().getTypeConverter().tryConvertTo(InputStream.class, exchange, body);
        if (is == null) {
            throw new IllegalArgumentException("Unsupported file type");
        }

        if (length < 0) {
            length = PayloadHelper.getLength(is);
        }
        if (length < 0) {
            // copy the data to determine the length, which uses stream caching so big payloads
            // are spooled to disk when spooling is enabled
            is = PayloadHelper.cacheStream(exchange, is);
            length = PayloadHelper.getLength(is);
        }
        return new FileStreamAndLength(is, length);
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public long getStreamLength() {
        return streamLength;
    }
}
