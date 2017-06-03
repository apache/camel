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
package org.apache.camel.dataformat.base64;

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.codec.binary.Base64OutputStream;

public class Base64DataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private int lineLength = Base64.MIME_CHUNK_SIZE;
    private byte[] lineSeparator = {'\r', '\n'};
    private boolean urlSafe;

    @Override
    public String getDataFormatName() {
        return "base64";
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        if (urlSafe) {
            marshalUrlSafe(exchange, graph, stream);
        } else {
            marshalStreaming(exchange, graph, stream);
        }
    }

    private void marshalStreaming(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        InputStream decoded = ExchangeHelper.convertToMandatoryType(exchange, InputStream.class, graph);

        Base64OutputStream base64Output = new Base64OutputStream(stream, true, lineLength, lineSeparator);
        try {
            IOHelper.copy(decoded, base64Output);
        } finally {
            IOHelper.close(decoded, base64Output);
        }
    }

    private void marshalUrlSafe(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        byte[] decoded = ExchangeHelper.convertToMandatoryType(exchange, byte[].class, graph);

        Base64 codec = new Base64(lineLength, lineSeparator, true);
        byte[] encoded = codec.encode(decoded);

        stream.write(encoded);
        stream.flush();
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream input) throws Exception {
        return new Base64InputStream(input, false, lineLength, lineSeparator);
    }

    public int getLineLength() {
        return lineLength;
    }

    public void setLineLength(int lineLength) {
        this.lineLength = lineLength;
    }

    public byte[] getLineSeparator() {
        return lineSeparator;
    }

    public void setLineSeparator(byte[] lineSeparator) {
        this.lineSeparator = lineSeparator;
    }

    public boolean isUrlSafe() {
        return urlSafe;
    }

    public void setUrlSafe(boolean urlSafe) {
        this.urlSafe = urlSafe;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
