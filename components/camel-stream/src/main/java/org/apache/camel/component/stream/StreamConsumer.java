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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Consumer that can read from any stream
 */

public class StreamConsumer extends DefaultConsumer<StreamExchange> {

    private static final String TYPES = "in";
    private static final String INVALID_URI = "Invalid uri, valid form: 'stream:{" + TYPES + "}'";
    private static final List<String> TYPES_LIST = Arrays.asList(TYPES.split(","));
    private static final Log LOG = LogFactory.getLog(StreamConsumer.class);
    protected InputStream inputStream = System.in;
    Endpoint<StreamExchange> endpoint;
    private Map<String, String> parameters;
    private String uri;


    public StreamConsumer(Endpoint<StreamExchange> endpoint, Processor processor, String uri,
                          Map<String, String> parameters) throws Exception {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.parameters = parameters;
        validateUri(uri);
        LOG.debug("Stream consumer created");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if ("in".equals(uri)) {
            inputStream = System.in;
        } else if ("file".equals(uri)) {
            inputStream = resolveStreamFromFile();
        } else if ("url".equals(uri)) {
            inputStream = resolveStreamFromUrl();
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        String line = null;
        try {
            while ((line = br.readLine()) != null) {
                consume(line);
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw new StreamComponentException(e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new StreamComponentException(e);
        }
    }

    public void consume(Object o) throws Exception {
        Exchange exchange = endpoint.createExchange();
        exchange.setIn(new StreamMessage(o));
        getProcessor().process(exchange);
    }

    private InputStream resolveStreamFromUrl() throws IOException {
        String u = parameters.get("url");
        URL url = new URL(u);
        URLConnection c = url.openConnection();
        return c.getInputStream();
    }

    private InputStream resolveStreamFromFile() throws IOException {
        String fileName = parameters.get("file");
        fileName = fileName != null ? fileName.trim() : "_file";
        File f = new File(fileName);
        LOG.debug("About to read from file: " + f);
        f.createNewFile();
        return new FileInputStream(f);
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
        if (inputStream != null) {
            inputStream.close();
        }
    }
}
