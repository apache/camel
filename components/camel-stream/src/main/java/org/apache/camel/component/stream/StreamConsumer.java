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
package org.apache.camel.component.stream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer that can read from streams
 */
public class StreamConsumer extends DefaultConsumer implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(StreamConsumer.class);

    private static final String TYPES = "in,file,http";
    private static final String INVALID_URI = "Invalid uri, valid form: 'stream:{" + TYPES + "}'";
    private static final List<String> TYPES_LIST = Arrays.asList(TYPES.split(","));
    private ExecutorService executor;
    private FileWatcherStrategy fileWatcher;
    private volatile boolean watchFileChanged;
    private volatile InputStream inputStream = System.in;
    private volatile InputStream inputStreamToClose;
    private volatile URLConnection urlConnectionToClose;
    private volatile File file;
    private StreamEndpoint endpoint;
    private String uri;
    private volatile boolean initialPromptDone;
    private final List<String> lines = new CopyOnWriteArrayList<>();

    public StreamConsumer(StreamEndpoint endpoint, Processor processor, String uri) throws Exception {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.uri = uri;
        validateUri(uri);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // use file watch service if we read from file
        if (endpoint.isFileWatcher()) {
            String dir = new File(endpoint.getFileName()).getParent();
            fileWatcher = new FileWatcherStrategy(dir, file -> {
                String onlyName = file.getName();
                String target = FileUtil.stripPath(endpoint.getFileName());
                LOG.trace("File changed: {}", onlyName);
                if (onlyName.equals(target)) {
                    // file is changed
                    watchFileChanged = true;
                }
            });
            fileWatcher.setCamelContext(getEndpoint().getCamelContext());
        }
        ServiceHelper.startService(fileWatcher);

        // if we scan the stream we are lenient and can wait for the stream to be available later
        if (!endpoint.isScanStream()) {
            initializeStreamLineMode();
        }

        executor = endpoint.getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this,
                endpoint.getEndpointUri());
        executor.execute(this);

        if (endpoint.getGroupLines() < 0) {
            throw new IllegalArgumentException(
                    "Option groupLines must be 0 or positive number, was " + endpoint.getGroupLines());
        }
    }

    @Override
    public void doStop() throws Exception {
        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            executor = null;
        }
        ServiceHelper.stopAndShutdownService(fileWatcher);
        lines.clear();

        IOHelper.close(inputStreamToClose);
        if (urlConnectionToClose != null) {
            closeURLConnection(urlConnectionToClose);
            urlConnectionToClose = null;
        }
        super.doStop();
    }

    @Override
    public void run() {
        try {
            if (endpoint.isReadLine()) {
                readFromStreamLineMode();
            } else {
                readFromStreamRawMode();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            getExceptionHandler().handleException(e);
        }
    }

    private BufferedReader initializeStreamLineMode() throws Exception {
        // close old stream, before obtaining a new stream
        IOHelper.close(inputStreamToClose);
        if (urlConnectionToClose != null) {
            closeURLConnection(urlConnectionToClose);
        }

        if ("in".equals(uri)) {
            inputStream = System.in;
            inputStreamToClose = null;
        } else if ("file".equals(uri)) {
            inputStream = resolveStreamFromFile();
            inputStreamToClose = inputStream;
        } else if ("http".equals(uri)) {
            inputStream = resolveStreamFromUrl();
            inputStreamToClose = inputStream;
        }

        if (inputStream != null) {
            if ("http".equals(uri)) {
                // read as-is
                return IOHelper.buffered(new InputStreamReader(inputStream));
            } else {
                Charset charset = endpoint.getCharset();
                return IOHelper.buffered(new InputStreamReader(inputStream, charset));
            }
        } else {
            return null;
        }
    }

    private InputStream initializeStreamRawMode() throws Exception {
        // close old stream, before obtaining a new stream
        IOHelper.close(inputStreamToClose);
        if (urlConnectionToClose != null) {
            closeURLConnection(urlConnectionToClose);
        }

        if ("in".equals(uri)) {
            inputStream = System.in;
            // do not close regular inputStream as it may be System.in etc.
            inputStreamToClose = null;
        } else if ("file".equals(uri)) {
            inputStream = resolveStreamFromFile();
            inputStreamToClose = inputStream;
        } else if ("http".equals(uri)) {
            inputStream = resolveStreamFromUrl();
            inputStreamToClose = inputStream;
        }

        return inputStream;
    }

    private void readFromStreamRawMode() throws Exception {
        long index = 0;
        InputStream is = initializeStreamRawMode();

        if (endpoint.isScanStream()) {
            // repeat scanning from stream
            while (isRunAllowed()) {

                byte[] data = null;
                try {
                    data = is.readAllBytes();
                } catch (IOException e) {
                    // ignore
                }
                boolean eos = data == null || data.length == 0;

                if (isRunAllowed() && endpoint.isRetry()) {
                    boolean reOpen = true;
                    if (endpoint.isFileWatcher()) {
                        reOpen = watchFileChanged;
                    }
                    if (reOpen) {
                        LOG.debug("File: {} changed/rollover, re-reading file from beginning", file);
                        is = initializeStreamRawMode();
                        // we have re-initialized the stream so lower changed flag
                        if (endpoint.isFileWatcher()) {
                            watchFileChanged = false;
                        }
                    } else {
                        LOG.trace("File: {} not changed since last read", file);
                    }
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
            byte[] data = null;
            while (!eos && isRunAllowed()) {
                if (endpoint.getPromptMessage() != null) {
                    doPromptMessage();
                }

                try {
                    data = is.readAllBytes();
                } catch (IOException e) {
                    // ignore
                }
                eos = data == null || data.length == 0;
                if (!eos) {
                    processRaw(data, index);
                }
            }
        }
    }

    private void readFromStreamLineMode() throws Exception {
        long index = 0;
        String line;
        BufferedReader br = initializeStreamLineMode();

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
                    boolean reOpen = true;
                    if (endpoint.isFileWatcher()) {
                        reOpen = watchFileChanged;
                    }
                    if (reOpen) {
                        LOG.debug("File: {} changed/rollover, re-reading file from beginning", file);
                        br = initializeStreamLineMode();
                        // we have re-initialized the stream so lower changed flag
                        if (endpoint.isFileWatcher()) {
                            watchFileChanged = false;
                        }
                    } else {
                        LOG.trace("File: {} not changed since last read", file);
                    }
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
    protected long processLine(String line, boolean last, long index) throws Exception {
        lock.lock();
        try {
            if (endpoint.getGroupLines() > 0) {
                // remember line
                if (line != null) {
                    lines.add(line);
                }

                // should we flush lines?
                if (!lines.isEmpty() && (lines.size() >= endpoint.getGroupLines() || last)) {
                    // spit out lines as we hit the size, or it was the last
                    List<String> copy = new ArrayList<>(lines);
                    Object body = endpoint.getGroupStrategy().groupLines(copy);
                    // remember to inc index when we create an exchange
                    Exchange exchange = createExchange(body, index++, last);

                    // clear lines
                    lines.clear();

                    getProcessor().process(exchange);
                }
            } else if (line != null) {
                // single line
                // remember to inc index when we create an exchange
                Exchange exchange = createExchange(line, index++, last);
                getProcessor().process(exchange);
            }

            return index;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Strategy method for processing the data
     */
    protected long processRaw(byte[] body, long index) throws Exception {
        lock.lock();
        try {
            Exchange exchange = createExchange(body, index++, true);
            getProcessor().process(exchange);
            return index;
        } finally {
            lock.unlock();
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

    private String readAhead(BufferedReader br) throws IOException {
        if (uri.equals("in")) {
            // do not read ahead with reading from system in
            return null;
        } else {
            return br.readLine();
        }
    }

    private InputStream resolveStreamFromFile() throws IOException {
        String fileName = endpoint.getFileName();
        StringHelper.notEmpty(fileName, "fileName");

        FileInputStream fileStream;

        file = new File(fileName);

        if (LOG.isDebugEnabled()) {
            LOG.debug("File to be scanned: {}, path: {}", file.getName(), file.getAbsolutePath());
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

    /**
     * From a comma-separated list of headers in the format of "FIELD=VALUE" or "FIELD:VALUE", split on the commas and
     * split on the separator to create a stream of Map.Entry values while filtering out invalid combinations
     *
     * @param  headerList A string containing a comma-separated list of headers
     * @return            A Stream of Map.Entry items which can then be added as headers to a URLConnection
     */
    Stream<Map.Entry<String, String>> parseHeaders(String headerList) {
        return Arrays.asList(headerList.split(","))
                .stream()
                .map(s -> s.split("[=:]"))
                .filter(h -> h.length == 2)
                .map(h -> Map.entry(h[0].trim(), h[1].trim()));
    }

    private InputStream resolveStreamFromUrl() throws IOException {
        String url = endpoint.getHttpUrl();
        StringHelper.notEmpty(url, "httpUrl");

        urlConnectionToClose = URI.create(url).toURL().openConnection();
        urlConnectionToClose.setUseCaches(false);
        String headers = endpoint.getHttpHeaders();
        if (headers != null) {
            parseHeaders(headers)
                    .forEach(e -> urlConnectionToClose.setRequestProperty(e.getKey(), e.getValue()));
        }

        InputStream is;

        try {
            is = urlConnectionToClose.getInputStream();
        } catch (IOException e) {
            // close the http connection to avoid
            // leaking gaps in case of an exception
            if (urlConnectionToClose instanceof HttpURLConnection) {
                ((HttpURLConnection) urlConnectionToClose).disconnect();
            }
            throw e;
        }

        return is;
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

    protected Exchange createExchange(Object body, long index, boolean last) {
        Exchange exchange = createExchange(true);
        exchange.getIn().setBody(body);
        exchange.getIn().setHeader(StreamConstants.STREAM_INDEX, index);
        exchange.getIn().setHeader(StreamConstants.STREAM_COMPLETE, last);
        return exchange;
    }

    private static void closeURLConnection(URLConnection con) {
        if (con instanceof HttpURLConnection) {
            try {
                ((HttpURLConnection) con).disconnect();
            } catch (Exception e) {
                // ignore
            }
        }
    }

}
