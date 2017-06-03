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
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer that can read from streams
 */
public class StreamConsumer extends DefaultConsumer implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(StreamConsumer.class);
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

        // if we scan the stream we are lenient and can wait for the stream to be available later
        if (!endpoint.isScanStream()) {
            initializeStream();
        }

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

        if (inputStream != null) {
            Charset charset = endpoint.getCharset();
            return IOHelper.buffered(new InputStreamReader(inputStream, charset));
        } else {
            return null;
        }
    }

    private void readFromStream() throws Exception {
        long index = 0;
        String line;
        BufferedReader br = initializeStream();

        if (endpoint.isScanStream()) {
            // repeat scanning from stream
            while (isRunAllowed()) {
                if (br != null) {
                    line = br.readLine();
                    LOG.trace("Read line: {}", line);
                } else {
                    line = null;
                }
                boolean eos = line == null;
                if (!eos && isRunAllowed()) {
                    index = processLine(line, false, index);
                } else if (eos && isRunAllowed() && endpoint.isRetry()) {
                    //try and re-open stream
                    br = initializeStream();
                }
                // sleep only if there is no input
                if (eos) {
                    try {
                        Thread.sleep(endpoint.getScanStreamDelay());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } else {
            // regular read stream once until end of stream
            boolean eos = false;
            String line2 = null;
            while (!eos && isRunAllowed()) {
                if (endpoint.getPromptMessage() != null) {
                    doPromptMessage();
                }

                if (line2 == null) {
                    line = br.readLine();
                } else {
                    line = line2;
                }
                LOG.trace("Read line: {}", line);

                eos = line == null;
                if (!eos && isRunAllowed()) {
                    // read ahead if there is more data
                    line2 = readAhead(br);
                    boolean last = line2 == null;
                    index = processLine(line, last, index);
                }
            }
            // EOL so trigger any
            processLine(null, true, index);
        }
        // important: do not close the reader as it will close the standard system.in etc.
    }

    /**
     * Strategy method for processing the line
     */
    protected synchronized long processLine(String line, boolean last, long index) throws Exception {
        if (endpoint.getGroupLines() > 0) {
            // remember line
            if (line != null) {
                lines.add(line);
            }

            // should we flush lines?
            if (!lines.isEmpty() && (lines.size() >= endpoint.getGroupLines() || last)) {
                // spit out lines as we hit the size, or it was the last
                List<String> copy = new ArrayList<String>(lines);
                Object body = endpoint.getGroupStrategy().groupLines(copy);
                // remember to inc index when we create an exchange
                Exchange exchange = endpoint.createExchange(body, index++, last);

                // clear lines
                lines.clear();

                getProcessor().process(exchange);
            }
        } else if (line != null) {
            // single line
            // remember to inc index when we create an exchange
            Exchange exchange = endpoint.createExchange(line, index++, last);
            getProcessor().process(exchange);
        }

        return index;
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

    private String readAhead(BufferedReader br) throws IOException {
        if (uri.equals("in")) {
            // do not read ahead with reading from system in
            return null;
        } else {
            return br.readLine();
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
        } else if (endpoint.isScanStream()) {
            // if we scan the stream then it may not be available and we should return null
            fileStream = null;
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
