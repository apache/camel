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
package org.apache.camel.component.azure.storage.blob;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.io.IOUtils;

public final class BlobUtils {

    private BlobUtils() {
    }

    public static Message getInMessage(final Exchange exchange) {
        return ObjectHelper.isEmpty(exchange) ? null : exchange.getIn();
    }

    public static Long getInputStreamLength(final InputStream inputStream) throws IOException {
        if (!inputStream.markSupported()) {
            throw new IllegalArgumentException(
                    "Reset inputStream is not supported, provide an inputstream with supported mark/reset.");
        }
        final long length = IOUtils.toByteArray(inputStream).length;
        inputStream.reset();

        return length;
    }

    public static String getContainerName(final BlobConfiguration configuration, final Exchange exchange) {
        return ObjectHelper.isEmpty(BlobExchangeHeaders.getBlobContainerNameFromHeaders(exchange))
                ? configuration.getContainerName()
                : BlobExchangeHeaders.getBlobContainerNameFromHeaders(exchange);
    }

    public static String getBlobName(final BlobConfiguration configuration, final Exchange exchange) {
        return ObjectHelper.isEmpty(BlobExchangeHeaders.getBlobNameFromHeaders(exchange))
                ? configuration.getBlobName()
                : BlobExchangeHeaders.getBlobNameFromHeaders(exchange);
    }

}
