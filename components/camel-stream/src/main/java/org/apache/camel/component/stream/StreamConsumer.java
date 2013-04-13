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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer that can read from streams
 */
public class StreamConsumer extends DefaultConsumer implements Runnable {

    private static final transient Logger LOG = LoggerFactory.getLogger(StreamConsumer.class);
    private static final String TYPES = "in,file,url";
    private static final String INVALID_URI = "Invalid uri, valid form: 'stream:{" + TYPES + "}'";
    private static final List<String> TYPES_LIST = Arrays.asList(TYPES.split(","));
    private ExecutorService executor;
    private volatile InputStream inputStream = System.in;
    private volatile InputStream inputStreamToClose;
    private StreamEndpoint endpoint;
    private String uri;
    private boolean initialPromptDone;
    private final List<String> lines = new CopyOnWriteArrayList<String>();

    public StreamConsumer(StreamEndpoint endpoint, Processor processor, String uri) throws Exception {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.uri = uri;
        validateUri(uri);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        initializeStream();

        executor = endpoint.getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, endpoint.getEndpointUri());
        executor.execute(this);

        if (endpoint.getGroupLines() < 0) {
            throw new IllegalArgumentException("Option groupLines must be 0 or positive number, was " + endpoint.getGroupLines());
        }
    }

    @Override
    public void doStop() throws Exception {
        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            executor = null;
        }
        lines.clear();

        // do not close regular inputStream as it may be System.in etc.
        IOHelper.close(inputStreamToClose);
        super.doStop();
    }

    public void run() {
        try {
            readFromStream();
        } catch (InterruptedException e) {
            // we are closing down so ignore
        } catch (Exception e) {
            getExceptionHandler().handleException(e);
        }
    }

    private BufferedReader initializeStream() throws Exception {
        // close old stream, before obtaining a new stream
        IOHelper.close(inputStreamToClose);

        if ("in".equals(uri)) {
            inputStream = System.in;
            inputStreamToClose = null;
        } else if ("file".equals(uri)) {
            inputStream = resolveStreamFromFile();
            inputStreamToClose = inputStream;
        } else if ("url".equals(uri)) {
            inputStream = resolveStreamFromUrl();
            inputStreamToClose = inputStream;
        }
        Charset charset = endpoint.getCharset();
        return IOHelper.buffered(new InputStreamReader(inputStream, charset));
    }

    private void readFromStream() throws Exception {
        String line;
        BufferedReader br = initializeStream();

        if (endpoint.isScanStream()) {
            // repeat scanning from stream
            while (isRunAllowed()) {
                line = br.readLine();
                LOG.trace("Read line: {}", line);
                boolean eos = line == null;
                if (!eos && isRunAllowed()) {
                    processLine(line);
                } else if (eos && isRunAllowed() && endpoint.isRetry()) {
                    //try and re-open stream
                    br = initializeStream();
                }
                try {
                    Thread.sleep(endpoint.getScanStreamDelay());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } else {
            // regular read stream once until end of stream
            boolean eos = false;
            while (!eos && isRunAllowed()) {
                if (endpoint.getPromptMessage() != null) {
                    doPromptMessage();
                }

                line = br.readLine();
                LOG.trace("Read line: {}", line);
                eos = line == null;
                if (!eos && isRunAllowed()) {
                    processLine(line);
                }
            }
        }
        // important: do not close the reader as it will close the standard system.in etc.
    }

    /**
     * Strategy method for processing the line
     */
    protected synchronized void processLine(String line) throws Exception {
        if (endpoint.getGroupLines() > 0) {
            // remember line
            lines.add(line);

            // should we flush lines?
            if (lines.size() >= endpoint.getGroupLines()) {
                // spit out lines
                Exchange exchange = endpoint.createExchange();

                // create message with the lines
                Message msg = new DefaultMessage();
                List<String> copy = new ArrayList<String>(lines);
                msg.setBody(endpoint.getGroupStrategy().groupLines(copy));
                exchange.setIn(msg);

                // clear lines
                lines.clear();

                getProcessor().process(exchange);
            }
        } else {
            // single line
            Exchange exchange = endpoint.createExchange();

            Message msg = new DefaultMessage();
            msg.setBody(line);
            exchange.setIn(msg);

            getProcessor().process(exchange);
        }
    }

    /**
     * Strategy method for prompting the prompt message
     */
    protected void doPromptMessage() {
        long delay = 0;

        if (!initialPromptDone && endpoint.getInitialPromptDelay() > 0) {
            initialPromptDone = true;
            delay = endpoint.getInitialPromptDelay();
        } else if (endpoint.getPromptDelay() > 0) {
            delay = endpoint.getPromptDelay();
        }

        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (inputStream == System.in) {
            System.out.print(endpoint.getPromptMessage());
        }
    }

    private InputStream resolveStreamFromUrl() throws IOException {
        String u = endpoint.getUrl();
        ObjectHelper.notEmpty(u, "url");
        LOG.debug("About to read from url: {}", u);

        URL url = new URL(u);
        URLConnection c = url.openConnection();
        return c.getInputStream();
    }

    private InputStream resolveStreamFromFile() throws IOException {
        String fileName = endpoint.getFileName();
        ObjectHelper.notEmpty(fileName, "fileName");
        
        FileInputStream fileStream;

        File file = new File(fileName);

        if (LOG.isDebugEnabled()) {
            LOG.debug("File to be scanned : {}, path : {}", file.getName(), file.getAbsolutePath());
        }

        if (file.canRead()) {
            fileStream = new FileInputStream(file);
        } else {
            throw new IllegalArgumentException(INVALID_URI);
        }

        return fileStream;
    }

    private void validateUri(String uri) throws IllegalArgumentException {
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
