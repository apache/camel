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
package org.apache.camel.support.builder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.support.ExchangeHelper;

/**
 * Utility to hide the complexity of choosing which OutputStream
 * implementation to choose.
 * <p/>
 * Itself masquerades as an OutputStream, but really delegates to a
 * CachedOutputStream of a ByteArrayOutputStream.
 */
public final class OutputStreamBuilder extends OutputStream {

    private final OutputStream outputStream;

    private OutputStreamBuilder(final Exchange exchange) {
        if (ExchangeHelper.isStreamCachingEnabled(exchange)) {
            outputStream = new CachedOutputStream(exchange);
        } else {
            outputStream = new ByteArrayOutputStream();
        }
    }

    /**
     * Creates a new OutputStreamBuilder with the current exchange
     * <p/>
     * Use the {@link #build()} when writing to the stream is finished,
     * and you need the result of this operation.
     *
     * @param exchange the current Exchange
     * @return the builder
     */
    public static OutputStreamBuilder withExchange(final Exchange exchange) {
        return new OutputStreamBuilder(exchange);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        outputStream.write(b, off, len);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        outputStream.write(b);
    }

    @Override
    public void write(final int b) throws IOException {
        outputStream.write(b);
    }

    @Override
    public void flush() throws IOException {
        outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }

    /**
     * Builds the result of using this builder as either a
     * {@link org.apache.camel.converter.stream.CachedOutputStream} if stream caching is enabled,
     * otherwise byte[].
     */
    public Object build() throws IOException {
        if (outputStream instanceof CachedOutputStream) {
            return ((CachedOutputStream)outputStream).newStreamCache();
        }
        return ((ByteArrayOutputStream)outputStream).toByteArray();
    }
}
