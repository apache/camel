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

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Producer that can write to streams
 */
public class StreamProducer extends DefaultProducer<Exchange> {

    private static final transient Log LOG = LogFactory.getLog(StreamProducer.class);
    private static final String TYPES = "out,err,file,header,url";
    private static final String INVALID_URI = "Invalid uri, valid form: 'stream:{" + TYPES + "}'";
    private static final List<String> TYPES_LIST = Arrays.asList(TYPES.split(","));
    private OutputStream outputStream = System.out;
    private StreamEndpoint endpoint;
    private String uri;

    public StreamProducer(StreamEndpoint endpoint, String uri)
        throws Exception {
        super(endpoint);
        this.endpoint = endpoint;
        validateUri(uri);
    }

    @Override
    public void doStop() throws Exception {
        // important: do not close the stream as it will close the standard system.out etc.
        super.doStop();
    }

    public void process(Exchange exchange) throws Exception {
        delay(endpoint.getDelay());

        if ("out".equals(uri)) {
            outputStream = System.out;
        } else if ("err".equals(uri)) {
            outputStream = System.err;
        } else if ("file".equals(uri)) {
            outputStream = resolveStreamFromFile();
        } else if ("header".equals(uri)) {
            outputStream = resolveStreamFromHeader(exchange.getIn().getHeader("stream"), exchange);
        } else if ("url".equals(uri)) {
            outputStream = resolveStreamFromUrl();
        }
        writeToStream(exchange);
    }

    private OutputStream resolveStreamFromUrl() throws IOException {
        String u = endpoint.getUrl();
        URL url = new URL(u);
        URLConnection c = url.openConnection();
        return c.getOutputStream();
    }

    private OutputStream resolveStreamFromFile() throws IOException {
        String fileName = endpoint.getFile() != null ? endpoint.getFile().trim() : "_file";
        File f = new File(fileName);
        if (LOG.isDebugEnabled()) {
            LOG.debug("About to write to file: " + f);
        }
        f.createNewFile();
        return new FileOutputStream(f);
    }

    private OutputStream resolveStreamFromHeader(Object o, Exchange exchange) throws CamelExchangeException {
        if (o != null && o instanceof OutputStream) {
            return (OutputStream)o;
        } else {
            throw new CamelExchangeException("Expected OutputStream in header('stream'), found: " + o,
                exchange);
        }
    }

    private void delay(long ms) throws InterruptedException {
        if (ms == 0) {
            return;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Delaying " + ms + " millis");
        }
        Thread.sleep(ms);
    }

    private void writeToStream(Exchange exchange) throws IOException, CamelExchangeException {
        Object body = exchange.getIn().getBody();
        if (body instanceof String) {
            Charset charset = endpoint.getCharset();
            Writer writer = new OutputStreamWriter(outputStream, charset);
            BufferedWriter bw = new BufferedWriter(writer);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Writing as text: " + body + " to " + outputStream + " using encoding:" + charset);
            }
            bw.write((String)body);
            bw.write("\n");
            bw.flush();
            // important: do not close the writer as it will close the standard system.out etc.
        } else if (body instanceof byte[]) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Writing as text: " + body + " to " + outputStream);
            }
            outputStream.write((byte[])body);
        } else {
            throw new CamelExchangeException("The body is neither a String or byte array. "
                + "Can not write body to output stream", exchange);
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

        if (!TYPES_LIST.contains(this.uri)) {
            throw new IllegalArgumentException(INVALID_URI);
        }
    }

}

