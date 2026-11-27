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

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read from System.in, a file or an HTTP URL, and write to System.out, System.err, a file or an output stream in a
 * message header.
 */
@UriEndpoint(firstVersion = "1.3.0", scheme = "stream", title = "Stream", syntax = "stream:kind",
             category = { Category.FILE, Category.CORE }, headersClass = StreamConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String", dependsOn = "groupLines",
                                   byOption = @BodyType.When(option = "readLine", value = "false", carriers = Carrier.BYTES,
                                                             javaType = "byte[]"),
                                   description = "Each line of the stream as text. With groupLines, a List of the lines; with"
                                                 + " readLine=false, all the bytes of the stream (with scanStream,"
                                                 + " the bytes read in one scan)"),
           producerIn = @BodyType(value = { Carrier.NULL, Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  description = "Text in the encoding, or bytes, a stream or a file as bytes, followed by a new"
                                                + " line; no body writes nothing. A Map or a POJO is written as its toString()"
                                                + " and a List fails: marshal it first"),
           producerOut = @BodyType(Carrier.UNCHANGED))
public class StreamEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(StreamEndpoint.class);

    private transient Charset charset;

    @UriPath(enums = "in,out,err,header,file,http",
             description = "The kind of stream. The consumer reads from in (System.in), file (the file of"
                           + " fileName) or http (the URL of httpUrl). The producer writes to out (System.out), err"
                           + " (System.err), file (the file of fileName) or header (the java.io.OutputStream in the"
                           + " stream header of the message). Any other kind fails when the consumer or producer is"
                           + " created.")
    @Metadata(required = true)
    private String kind;
    @UriParam(description = "The path of the file that kind file reads or writes. The consumer reads the file from"
                            + " the beginning; without scanStream, a file that cannot be read fails the start of the"
                            + " consumer. The producer appends to the file, and creates it and its parent directories"
                            + " when they do not exist.")
    private String fileName;
    @UriParam(label = "consumer",
              description = "The URL that kind http reads from, which the consumer opens with"
                            + " java.net.URLConnection. Without scanStream, the consumer opens it when it starts, so a"
                            + " URL that cannot be reached fails the start.")
    private String httpUrl;
    @UriParam(label = "consumer",
              description = "The request headers that the consumer sends to httpUrl, as a comma-separated list of"
                            + " name=value or name:value pairs, for example Accept:application/json. Whitespace around"
                            + " each name and value is removed. A value that contains a comma, = or : is not"
                            + " supported.")
    private String httpHeaders;
    @UriParam(label = "consumer",
              description = "Whether the consumer keeps reading the stream after its end, like the Unix tail -f"
                            + " command, and waits scanStreamDelay each time it finds no new data. The consumer opens"
                            + " the stream in its reading thread, so a file that does not exist yet or a URL that"
                            + " cannot be reached does not fail the start, and is opened again later only with retry."
                            + " A scanned stream never completes, so the CamelStreamComplete header is always false.")
    private boolean scanStream;
    @UriParam(label = "consumer",
              description = "Whether the consumer opens the stream again at its end and after it failed to open or"
                            + " read it, somewhat like tail --retry. Only used with scanStream. A file is then read"
                            + " again from the beginning, and the request to httpUrl is sent again. Without"
                            + " fileWatcher this happens at each end of the stream, so enable fileWatcher to read a"
                            + " file again only after it changed.")
    private boolean retry;
    @UriParam(label = "consumer",
              description = "Whether the consumer watches the directory of fileName with the JDK WatchService for"
                            + " the creation or modification of the file, for example when it is overwritten or rolled"
                            + " over. Only for kind file, and only used with scanStream and retry: the consumer then"
                            + " opens the file again at its end only when it changed. A fileName without a directory"
                            + " is not watched.")
    private boolean fileWatcher;
    @UriParam(label = "producer",
              description = "Whether the producer closes the file of kind file after the last message of a"
                            + " Splitter, which is the message with the exchange property CamelSplitComplete set to"
                            + " true. The next message opens the file again. Without it and autoCloseCount, the"
                            + " producer keeps the file open until it stops.")
    private boolean closeOnDone;
    @UriParam(label = "consumer",
              description = "The delay in milliseconds before the consumer reads again when it finds no new data in"
                            + " the stream. Only used with scanStream. After the stream could not be opened or read,"
                            + " the consumer waits at least one second.")
    private long scanStreamDelay;
    @UriParam(label = "producer",
              description = "The delay in milliseconds before the producer writes each message.")
    private long delay;
    @UriParam(description = "The name of the charset of text, for example UTF-8. The consumer uses it to read lines"
                            + " with kind in or file, reads lines of kind http with the JVM default charset, and does"
                            + " not use it with readLine=false. The producer uses it to write a body that is a String"
                            + " or that cannot be converted to byte[]. If not set, the JVM default charset is used.")
    private String encoding;
    @UriParam(label = "consumer",
              description = "The text that the consumer prints to System.out before it reads each line from"
                            + " System.in, for example Enter a command: (no new line is added). Only printed for kind"
                            + " in, and not with scanStream.")
    private String promptMessage;
    @UriParam(label = "consumer",
              description = "The delay in milliseconds before each prompt of promptMessage, except the first one"
                            + " when initialPromptDelay is set.")
    private long promptDelay;
    @UriParam(label = "consumer", defaultValue = "2000",
              description = "The delay in milliseconds before the first prompt of promptMessage, used in place of"
                            + " promptDelay. It keeps the prompt apart from the log output that is written to"
                            + " System.out while the application starts. If 0, the first prompt waits promptDelay.")
    private long initialPromptDelay = 2000;
    @UriParam(label = "consumer",
              description = "The number of lines that the consumer puts in one exchange, whose body is the result"
                            + " of groupStrategy, by default a List of the lines. If 0, each line is one exchange."
                            + " Without scanStream, the lines left at the end of the stream are sent as a smaller"
                            + " group, and with scanStream a group is sent only when it is full. With kind in and"
                            + " without scanStream, each line is sent alone. Not used with readLine=false.")
    private int groupLines;
    @UriParam(label = "consumer", defaultValue = "true",
              description = "Whether the consumer reads the stream line by line and creates an exchange with a"
                            + " String body for each line, or for each group of groupLines lines. If false, the"
                            + " consumer reads the stream to its end and creates one exchange with all the bytes as"
                            + " byte[] body; with scanStream, each exchange holds the bytes read in one scan.")
    private boolean readLine = true;
    @UriParam(label = "producer", defaultValue = "true",
              description = "Whether the producer writes the line separator of the operating system"
                            + " (System.lineSeparator()) after each message body.")
    private boolean appendNewLine = true;
    @UriParam(label = "producer",
              description = "The number of messages after which the producer closes the file of kind file. The next"
                            + " message opens the file again for another batch of messages. If 0, the producer keeps"
                            + " the file open until it stops. System.out and System.err are never closed, and the"
                            + " stream of kind header is closed after each message.")
    private int autoCloseCount;
    @UriParam(label = "consumer",
              description = "The org.apache.camel.component.stream.GroupStrategy that turns a group of lines into"
                            + " the message body. Only used when groupLines is set. The default returns the List of"
                            + " the lines as is.")
    private GroupStrategy groupStrategy = new DefaultGroupStrategy();
    @UriParam(label = "advanced",
              description = "Not used: the component applies no read timeout to any stream, including the"
                            + " connection to httpUrl.")
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

    // Properties
    //-------------------------------------------------------------------------

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getHttpUrl() {
        return httpUrl;
    }

    public void setHttpUrl(String httpUrl) {
        this.httpUrl = httpUrl;
    }

    public String getHttpHeaders() {
        return httpHeaders;
    }

    public void setHttpHeaders(String httpHeaders) {
        this.httpHeaders = httpHeaders;
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

    public boolean isFileWatcher() {
        return fileWatcher;
    }

    public void setFileWatcher(boolean fileWatcher) {
        this.fileWatcher = fileWatcher;
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

    public boolean isReadLine() {
        return readLine;
    }

    public void setReadLine(boolean readLine) {
        this.readLine = readLine;
    }

    public int getAutoCloseCount() {
        return autoCloseCount;
    }

    public void setAutoCloseCount(int autoCloseCount) {
        this.autoCloseCount = autoCloseCount;
    }

    public boolean isAppendNewLine() {
        return appendNewLine;
    }

    public void setAppendNewLine(boolean appendNewLine) {
        this.appendNewLine = appendNewLine;
    }

    public Charset getCharset() {
        return charset;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    // Implementations
    //-------------------------------------------------------------------------

    @Override
    protected void doStart() throws Exception {
        super.doStart();
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
