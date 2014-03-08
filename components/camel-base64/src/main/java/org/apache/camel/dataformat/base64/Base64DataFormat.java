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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.commons.codec.binary.Base64;

public class Base64DataFormat implements DataFormat {

    private int lineLength = Base64.MIME_CHUNK_SIZE;
    private byte[] lineSeparator = {'\r', '\n'};
    private boolean urlSafe;

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        byte[] decoded = ExchangeHelper.convertToMandatoryType(exchange, byte[].class, graph);

        Base64 codec = createCodec();
        byte[] encoded = codec.encode(decoded);

        stream.write(encoded);
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream input) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(input, baos);
        byte[] encoded = baos.toByteArray();
        baos.close();

        Base64 codec = createCodec();
        byte[] decoded = codec.decode(encoded);

        return decoded;
    }

    private Base64 createCodec() {
        return new Base64(lineLength, lineSeparator, urlSafe);
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
}
