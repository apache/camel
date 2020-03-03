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

import java.nio.charset.Charset;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The stream: component provides access to the system-in, system-out and system-err streams as well as allowing streaming of file.
 */
@UriEndpoint(firstVersion = "1.3.0", scheme = "stream", title = "Stream", syntax = "stream:kind", label = "file,system")
public class StreamEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(StreamEndpoint.class);

    private transient Charset charset;

    @UriPath(enums = "in,out,err,header,file") @Metadata(required = true)
    private String kind;
    @UriParam
    private String fileName;
    @UriParam(label = "consumer")
    private boolean scanStream;
    @UriParam(label = "consumer")
    private boolean retry;
    @UriParam(label = "consumer")
    private boolean fileWatcher;
    @UriParam(label = "producer")
    private boolean closeOnDone;
    @UriParam(label = "consumer")
    private long scanStreamDelay;
    @UriParam(label = "producer")
    private long delay;
    @UriParam
    private String encoding;
    @UriParam(label = "consumer")
    private String promptMessage;
    @UriParam(label = "consumer")
    private long promptDelay;
    @UriParam(label = "consumer", defaultValue = "2000")
    private long initialPromptDelay = 2000;
    @UriParam(label = "consumer")
    private int groupLines;
    @UriParam(label = "producer")
    private int autoCloseCount;
    @UriParam(label = "consumer")
    private GroupStrategy groupStrategy = new DefaultGroupStrategy();
    @UriParam(label = "advanced")
    private int readTimeout;

    public StreamEndpoint(String endpointUri, Component component) throws Exception {
        super(endpointUri, component);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        StreamConsumer answer = new StreamConsumer(this, processor, getEndpointUri());
        if (isFileWatcher() && !"file".equals(getKind())) {
            throw new IllegalArgumentException("File watcher is only possible if reading streams from files");
        }
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new StreamProducer(this, getEndpointUri());
    }

    protected Exchange createExchange(Object body, long index, boolean last) {
        Exchange exchange = createExchange();
        exchange.getIn().setBody(body);
        exchange.getIn().setHeader(StreamConstants.STREAM_INDEX, index);
        exchange.getIn().setHeader(StreamConstants.STREAM_COMPLETE, last);
        return exchange;
    }

    // Properties
    //-------------------------------------------------------------------------


    public String getKind() {
        return kind;
    }

    /**
     * Kind of stream to use such as System.in or System.out.
     */
    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getFileName() {
        return fileName;
    }

    /**
     * When using the stream:file URI format, this option specifies the filename to stream to/from.
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getDelay() {
        return delay;
    }

    /**
     * Initial delay in milliseconds before producing the stream.
     */
    public void setDelay(long delay) {
        this.delay = delay;
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * You can configure the encoding (is a charset name) to use text-based streams (for example, message body is a String object).
     * If not provided, Camel uses the JVM default Charset.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getPromptMessage() {
        return promptMessage;
    }

    /**
     * Message prompt to use when reading from stream:in; for example, you could set this to Enter a command:
     */
    public void setPromptMessage(String promptMessage) {
        this.promptMessage = promptMessage;
    }

    public long getPromptDelay() {
        return promptDelay;
    }

    /**
     * Optional delay in milliseconds before showing the message prompt.
     */
    public void setPromptDelay(long promptDelay) {
        this.promptDelay = promptDelay;
    }

    public long getInitialPromptDelay() {
        return initialPromptDelay;
    }

    /**
     * Initial delay in milliseconds before showing the message prompt. This delay occurs only once.
     * Can be used during system startup to avoid message prompts being written while other logging is done to the system out.
     */
    public void setInitialPromptDelay(long initialPromptDelay) {
        this.initialPromptDelay = initialPromptDelay;
    }

    public boolean isScanStream() {
        return scanStream;
    }

    /**
     * To be used for continuously reading a stream such as the unix tail command.
     */
    public void setScanStream(boolean scanStream) {
        this.scanStream = scanStream;
    }
    
    public GroupStrategy getGroupStrategy() {
        return groupStrategy;
    }

    /**
     * Allows to use a custom GroupStrategy to control how to group lines.
     */
    public void setGroupStrategy(GroupStrategy strategy) {
        this.groupStrategy = strategy;
    }

    public boolean isRetry() {
        return retry;
    }

    /**
     * Will retry opening the stream if it's overwritten, somewhat like tail --retry
     * <p/>
     * If reading from files then you should also enable the fileWatcher option, to make it work reliable.
     */
    public void setRetry(boolean retry) {
        this.retry = retry;
    }

    public boolean isFileWatcher() {
        return fileWatcher;
    }

    /**
     * To use JVM file watcher to listen for file change events to support re-loading files that may be overwritten, somewhat like tail --retry
     */
    public void setFileWatcher(boolean fileWatcher) {
        this.fileWatcher = fileWatcher;
    }

    public boolean isCloseOnDone() {
        return closeOnDone;
    }

    /**
     * This option is used in combination with Splitter and streaming to the same file.
     * The idea is to keep the stream open and only close when the Splitter is done, to improve performance.
     * Mind this requires that you only stream to the same file, and not 2 or more files.
     */
    public void setCloseOnDone(boolean closeOnDone) {
        this.closeOnDone = closeOnDone;
    }

    public long getScanStreamDelay() {
        return scanStreamDelay;
    }

    /**
     * Delay in milliseconds between read attempts when using scanStream.
     */
    public void setScanStreamDelay(long scanStreamDelay) {
        this.scanStreamDelay = scanStreamDelay;
    }

    public int getGroupLines() {
        return groupLines;
    }

    /**
     * To group X number of lines in the consumer.
     * For example to group 10 lines and therefore only spit out an Exchange with 10 lines, instead of 1 Exchange per line.
     */
    public void setGroupLines(int groupLines) {
        this.groupLines = groupLines;
    }
    
    public int getAutoCloseCount() {
        return autoCloseCount;
    }

    /**
     * Number of messages to process before closing stream on Producer side.
     * Never close stream by default (only when Producer is stopped). If more messages are sent, the stream is reopened for another autoCloseCount batch.
     */
    public void setAutoCloseCount(int autoCloseCount) {
        this.autoCloseCount = autoCloseCount;
    }

    public Charset getCharset() {
        return charset;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    /**
     * Sets the read timeout to a specified timeout, in
     * milliseconds. A non-zero value specifies the timeout when
     * reading from Input stream when a connection is established to a
     * resource. If the timeout expires before there is data available
     * for read, a java.net.SocketTimeoutException is raised. A
     * timeout of zero is interpreted as an infinite timeout.
     */
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    // Implementations
    //-------------------------------------------------------------------------

    @Override
    protected void doStart() throws Exception {
        charset = loadCharset();
    }
    
    Charset loadCharset() {
        if (encoding == null) {
            encoding = Charset.defaultCharset().name();
            LOG.debug("No encoding parameter using default charset: {}", encoding);
        }
        if (!Charset.isSupported(encoding)) {
            throw new IllegalArgumentException("The encoding: " + encoding + " is not supported");
        }

        return Charset.forName(encoding);
    }
}
