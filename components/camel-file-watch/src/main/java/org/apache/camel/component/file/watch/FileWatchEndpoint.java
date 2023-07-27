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
package org.apache.camel.component.file.watch;

import io.methvin.watcher.hashing.FileHasher;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Get notified about file events in a directory using {@link java.nio.file.WatchService}.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "file-watch", title = "File Watch", syntax = "file-watch:path",
             category = { Category.FILE }, consumerOnly = true, headersClass = FileWatchConstants.class)
public class FileWatchEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {

    @UriPath(description = "Path of directory to consume events from.")
    @Metadata(required = true)
    private String path;
    @UriParam(description = "Comma separated list of events to watch. Possible values: CREATE,MODIFY,DELETE",
              defaultValue = "CREATE,MODIFY,DELETE")
    private String events = "CREATE,MODIFY,DELETE";
    @UriParam(description = "Auto create directory if does not exists.", defaultValue = "true")
    private boolean autoCreate = true;
    @UriParam(description = "Watch recursive in current and child directories (including newly created directories).",
              defaultValue = "true")
    private boolean recursive = true;
    @UriParam(label = "advanced",
              description = "The number of concurrent consumers. Increase this value, if your route is slow to prevent buffering in queue.",
              defaultValue = "1")
    private int concurrentConsumers;
    @UriParam(label = "advanced",
              description = "The number of threads polling WatchService. Increase this value, if you see OVERFLOW messages in log.",
              defaultValue = "1")
    private int pollThreads;
    @UriParam(description = "ANT style pattern to match files. The file is matched against path relative to endpoint path. "
                            + "Pattern must be also relative (not starting with slash)",
              defaultValue = "**")
    private String antInclude;
    @UriParam(label = "advanced",
              description = "Maximum size of queue between WatchService and consumer. Unbounded by default.",
              defaultValue = "" + Integer.MAX_VALUE)
    private int queueSize;
    @UriParam(label = "advanced",
              description = "Reference to io.methvin.watcher.hashing.FileHasher. "
                            + "This prevents emitting duplicate events on some platforms. "
                            + "For working with large files and if you dont need detect multiple modifications per second per file, "
                            + "use #lastModifiedTimeFileHasher. You can also provide custom implementation in registry.",
              defaultValue = "#murmur3FFileHasher")
    private FileHasher fileHasher;
    @UriParam(description = "Enables or disables file hashing to detect duplicate events. "
                            + "If you disable this, you can get some events multiple times on some platforms and JDKs. "
                            + "Check java.nio.file.WatchService limitations for your target platform.",
              defaultValue = "true")
    private boolean useFileHashing;

    public FileWatchEndpoint() {
    }

    public FileWatchEndpoint(String uri, FileWatchComponent component) {
        super(uri, component);
        setFileHasher(component.getFileHasher());
        setConcurrentConsumers(component.getConcurrentConsumers());
        setPollThreads(component.getPollThreads());
        setQueueSize(component.getQueueSize());
        setUseFileHashing(component.isUseFileHashing());
    }

    public FileWatchEndpoint(String uri, String remaining, FileWatchComponent component) {
        this(uri, component);
        setPath(remaining);
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("This component does not support producer");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        FileWatchConsumer consumer = new FileWatchConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getEvents() {
        return events;
    }

    public void setEvents(String events) {
        this.events = events;
    }

    public boolean isAutoCreate() {
        return autoCreate;
    }

    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public int getPollThreads() {
        return pollThreads;
    }

    public void setPollThreads(int pollThreads) {
        this.pollThreads = pollThreads;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public String getAntInclude() {
        return antInclude;
    }

    public void setAntInclude(String antInclude) {
        this.antInclude = antInclude;
    }

    public FileHasher getFileHasher() {
        return fileHasher;
    }

    public void setFileHasher(FileHasher fileHasher) {
        this.fileHasher = fileHasher;
    }

    public boolean isUseFileHashing() {
        return useFileHashing;
    }

    public void setUseFileHashing(boolean useFileHashing) {
        this.useFileHashing = useFileHashing;
    }

    @Override
    public FileWatchComponent getComponent() {
        return (FileWatchComponent) super.getComponent();
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        return true;
    }
}
