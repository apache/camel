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
package org.apache.camel.component.stream;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer that can write to streams
 */
public class StreamProducer extends DefaultProducer {

    private static final transient Logger LOG = LoggerFactory.getLogger(StreamProducer.class);
    private static final String TYPES = "out,err,file,header,url";
    private static final String INVALID_URI = "Invalid uri, valid form: 'stream:{" + TYPES + "}'";
    private static final List<String> TYPES_LIST = Arrays.asList(TYPES.split(","));
    private StreamEndpoint endpoint;
    private String uri;
    private OutputStream outputStream;
    private AtomicInteger count = new AtomicInteger();

    public StreamProducer(StreamEndpoint endpoint, String uri) throws Exception {
        super(endpoint);
        this.endpoint = endpoint;
        validateUri(uri);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        closeStream(true);
    }

    public void process(Exchange exchange) throws Exception {
        delay(endpoint.getDelay());

        synchronized (this) {
            openStream(exchange);
            writeToStream(outputStream, exchange);
            closeStream(false);
        }
    }

    private OutputStream resolveStreamFromUrl() throws IOException {
        String u = endpoint.getUrl();
        ObjectHelper.notEmpty(u, "url");
        LOG.debug("About to write to url: {}", u);

        URL url = new URL(u);
        URLConnection c = url.openConnection();
        return c.getOutputStream();
    }

    private OutputStream resolveStreamFromFile() throws IOException {
        String fileName = endpoint.getFileName();
        ObjectHelper.notEmpty(fileName, "fileName");
        LOG.debug("About to write to file: {}", fileName);
        File f = new File(fileName);
        // will create a new file if missing or append to existing
        f.createNewFile();
        return new FileOutputStream(f, true);
    }

    private OutputStream resolveStreamFromHeader(Object o, Exchange exchange) throws CamelExchangeException {
        return exchange.getContext().getTypeConverter().convertTo(OutputStream.class, o);
    }

    private void delay(long ms) throws InterruptedException {
        if (ms == 0) {
            return;
        }
        LOG.trace("Delaying {} millis", ms);
        Thread.sleep(ms);
    }

    private synchronized void writeToStream(OutputStream outputStream, Exchange exchange) throws IOException, CamelExchangeException {
        Object body = exchange.getIn().getBody();

        if (body == null) {
            log.debug("Body is null, cannot write it to the stream.");
            return;
        }

        // if not a string then try as byte array first
        if (!(body instanceof String)) {
            byte[] bytes = exchange.getIn().getBody(byte[].class);
            if (bytes != null) {
                LOG.debug("Writing as byte[]: {} to {}", bytes, outputStream);
                outputStream.write(bytes);
                return;
            }
        }

        // okay now fallback to mandatory converterable to string
        String s = exchange.getIn().getMandatoryBody(String.class);
        Charset charset = endpoint.getCharset();
        Writer writer = new OutputStreamWriter(outputStream, charset);
        BufferedWriter bw = IOHelper.buffered(writer);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Writing as text: {} to {} using encoding: {}", new Object[]{body, outputStream, charset});
        }
        bw.write(s);
        bw.write("\n");
        bw.flush();
    }

    private synchronized void openStream() throws Exception {
        if (outputStream != null) {
            return;
        }

        if ("out".equals(uri)) {
            outputStream = System.out;
        } else if ("err".equals(uri)) {
            outputStream = System.err;
        } else if ("file".equals(uri)) {
            outputStream = resolveStreamFromFile();
        } else if ("url".equals(uri)) {
            outputStream = resolveStreamFromUrl();
        }
        count.set(outputStream == null ? 0 : endpoint.getAutoCloseCount());
        LOG.debug("Opened stream '{}'", endpoint.getEndpointKey());
    }

    private synchronized void openStream(final Exchange exchange) throws Exception {
        if (outputStream != null) {
            return;
        }
        if ("header".equals(uri)) {
            outputStream = resolveStreamFromHeader(exchange.getIn().getHeader("stream"), exchange);
            LOG.debug("Opened stream '{}'", endpoint.getEndpointKey());
        } else {
            openStream();
        }
    }

    private synchronized void closeStream(boolean force) throws Exception {
        if (outputStream == null) {
            return;
        }

        // never close a standard stream (system.out or system.err)
        // always close a 'header' stream (unless it's a system stream)
        boolean systemStream = outputStream != System.out || outputStream != System.err;
        boolean headerStream = "header".equals(uri) && !systemStream;
        boolean reachedLimit = endpoint.getAutoCloseCount() > 0 && count.decrementAndGet() <= 0;
        boolean expiredStream = force || headerStream || reachedLimit;  // evaluation order is important!

        if (expiredStream) {
            outputStream.close();
            outputStream = null;
            LOG.debug("Closed stream '{}'", endpoint.getEndpointKey());
        }
    }

    private void validateUri(String uri) throws Exception {
        String[] s = uri.split(":");
        if (s.length < 2) {
            throw new IllegalArgumentException(INVALID_URI);
        }
        String[] t = s[1].split("\\?");

        if (t.length < 1) {
            throw new IllegalArgumentException(INVALID_URI);
        }
        this.uri = t[0].trim();
        if (this.uri.startsWith("//")) {
            this.uri = this.uri.substring(2);
        }

        if (!TYPES_LIST.contains(this.uri)) {
            throw new IllegalArgumentException(INVALID_URI);
        }
    }
}
