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

import java.nio.charset.Charset;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriEndpoint(scheme = "stream", title = "Stream", syntax = "stream:url", consumerClass = StreamConsumer.class, label = "file,system")
public class StreamEndpoint extends DefaultEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(StreamEndpoint.class);

    @UriPath @Metadata(required = "true")
    private String url;
    @UriParam
    private String fileName;
    @UriParam
    private boolean scanStream;
    @UriParam
    private boolean retry;
    @UriParam
    private boolean closeOnDone;
    @UriParam
    private long scanStreamDelay;
    @UriParam
    private long delay;
    @UriParam
    private String encoding;
    @UriParam
    private String promptMessage;
    @UriParam
    private long promptDelay;
    @UriParam(defaultValue = "2000")
    private long initialPromptDelay = 2000;
    @UriParam
    private int groupLines;
    @UriParam
    private int autoCloseCount;
    @UriParam
    private Charset charset;
    @UriParam
    private GroupStrategy groupStrategy = new DefaultGroupStrategy();

    public StreamEndpoint(String endpointUri, Component component) throws Exception {
        super(endpointUri, component);
    }

    @Deprecated
    public StreamEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        StreamConsumer answer = new StreamConsumer(this, processor, getEndpointUri());
        configureConsumer(answer);
        return answer;
    }

    public Producer createProducer() throws Exception {
        return new StreamProducer(this, getEndpointUri());
    }

    public boolean isSingleton() {
        return true;
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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getPromptMessage() {
        return promptMessage;
    }

    public void setPromptMessage(String promptMessage) {
        this.promptMessage = promptMessage;
    }

    public long getPromptDelay() {
        return promptDelay;
    }

    public void setPromptDelay(long promptDelay) {
        this.promptDelay = promptDelay;
    }

    public long getInitialPromptDelay() {
        return initialPromptDelay;
    }

    public void setInitialPromptDelay(long initialPromptDelay) {
        this.initialPromptDelay = initialPromptDelay;
    }

    public boolean isScanStream() {
        return scanStream;
    }

    public void setScanStream(boolean scanStream) {
        this.scanStream = scanStream;
    }
    
    public GroupStrategy getGroupStrategy() {
        return groupStrategy;
    }
    
    public void setGroupStrategy(GroupStrategy strategy) {
        this.groupStrategy = strategy;
    }

    public boolean isRetry() {
        return retry;
    }

    public void setRetry(boolean retry) {
        this.retry = retry;
    }
    
    public boolean isCloseOnDone() {
        return closeOnDone;
    }
    
    public void setCloseOnDone(boolean closeOnDone) {
        this.closeOnDone = closeOnDone;
    }

    public long getScanStreamDelay() {
        return scanStreamDelay;
    }

    public void setScanStreamDelay(long scanStreamDelay) {
        this.scanStreamDelay = scanStreamDelay;
    }

    public int getGroupLines() {
        return groupLines;
    }

    public void setGroupLines(int groupLines) {
        this.groupLines = groupLines;
    }
    
    public int getAutoCloseCount() {
        return autoCloseCount;
    }

    public void setAutoCloseCount(int autoCloseCount) {
        this.autoCloseCount = autoCloseCount;
    }

    public Charset getCharset() {
        return charset;
    }

    // Implementations
    //-------------------------------------------------------------------------

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
