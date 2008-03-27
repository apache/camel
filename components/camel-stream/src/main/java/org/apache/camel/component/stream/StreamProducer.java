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
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class StreamProducer extends DefaultProducer<StreamExchange> {

    private static final Log LOG = LogFactory.getLog(StreamProducer.class);
    private static final String TYPES = "in,out,err,file,url,header";
    private static final String INVALID_URI = "Invalid uri, valid form: 'stream:{" + TYPES + "}'";
    private static final List<String> TYPES_LIST = Arrays.asList(TYPES.split(","));
    protected OutputStream outputStream = System.out;
    private String uri;
    private Map<String, String> parameters;


    public StreamProducer(Endpoint<StreamExchange> endpoint, String uri, Map<String, String> parameters)
        throws Exception {
        super(endpoint);
        this.parameters = parameters;
        validateUri(uri);
        LOG.debug("Stream producer created");
    }

    public void process(Exchange exchange) throws Exception {
        if (parameters.get("delay") != null) {
            long ms = Long.valueOf(parameters.get("delay"));
            delay(ms);
        }
        if ("out".equals(uri)) {
            outputStream = System.out;
        } else if ("err".equals(uri)) {
            outputStream = System.err;
        } else if ("file".equals(uri)) {
            outputStream = resolveStreamFromFile();
        } else if ("header".equals(uri)) {
            outputStream = resolveStreamFromHeader(exchange.getIn().getHeader("stream"));
        } else if ("url".equals(uri)) {
            outputStream = resolveStreamFromUrl();
        }
        writeToStream(exchange);
    }

    private OutputStream resolveStreamFromUrl() throws IOException {
        String u = parameters.get("url");
        URL url = new URL(u);
        URLConnection c = url.openConnection();
        return c.getOutputStream();
    }

    private OutputStream resolveStreamFromFile() throws IOException {
        String fileName = parameters.get("file");
        fileName = fileName != null ? fileName.trim() : "_file";
        File f = new File(fileName);
        LOG.debug("About to write to file: " + f);
        f.createNewFile();
        return new FileOutputStream(f);
    }

    private OutputStream resolveStreamFromHeader(Object o) throws StreamComponentException {
        if (o != null && o instanceof OutputStream) {
            return (OutputStream)o;
        } else {
            throw new StreamComponentException("Expected OutputStream in header('stream'), found: " + o);
        }
    }

    private void delay(long ms) throws InterruptedException {
        LOG.debug("Delaying " + ms + " milliseconds");
        Thread.sleep(ms);
    }

    private void writeToStream(Exchange exchange) throws IOException {
        Object body = exchange.getIn().getBody();
        LOG.debug("Writing " + body + " to " + outputStream);
        if (body instanceof String) {
            LOG.debug("in text buffered mode");
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(outputStream));
            bw.write((String)body + "\n");
            bw.flush();
        } else {
            LOG.debug("in binary stream mode");
            outputStream.write((byte[])body);
        }
    }

    private void validateUri(String uri) throws Exception {
        String[] s = uri.split(":");
        if (s.length < 2) {
            throw new Exception(INVALID_URI);
        }
        String[] t = s[1].split("\\?");

        if (t.length < 1) {
            throw new Exception(INVALID_URI);
        }
        this.uri = t[0].trim();

        if (!TYPES_LIST.contains(this.uri)) {
            throw new Exception(INVALID_URI);
        }
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (outputStream != null) {
            outputStream.close();
        }
    }
}
