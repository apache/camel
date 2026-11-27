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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Receive an event when a file or directory is created, modified or deleted in a directory of the file system.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "file-watch", title = "File Watch", syntax = "file-watch:path",
             category = { Category.FILE }, consumerOnly = true, headersClass = FileWatchConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.FILE, javaType = "java.io.File",
                                   description = "The java.io.File of the file or directory of the event, also for DELETE, with"
                                                 + " the event in the CamelFileEventType header. The consumer does not read it:"
                                                 + " it converts to text, bytes or a stream by reading the file, which fails"
                                                 + " after a DELETE"))
public class FileWatchEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {

    @UriPath(description = "The directory to watch, as an absolute path or a path relative to the working directory of the JVM."
                           + " The consumer fails to start when the path is not a directory, or when it does not exist and"
                           + " autoCreate is false.")
    @Metadata(required = true)
    private String path;
    @UriParam(description = "The types of the events to receive, as a comma-separated list of CREATE, MODIFY and DELETE, in"
                            + " any case and with or without spaces around the values. An unknown value fails the start of"
                            + " the consumer. The events of the other types are dropped.",
              defaultValue = "CREATE,MODIFY,DELETE", enums = "CREATE,MODIFY,DELETE", multiEnum = true)
    private String events = "CREATE,MODIFY,DELETE";
    @UriParam(description = "Whether the consumer creates the directory, and its missing parent directories, when it does not"
                            + " exist at start. If false, the consumer fails to start when the directory does not exist.",
              defaultValue = "true")
    private boolean autoCreate = true;
    @UriParam(description = "Whether to receive the events of the files and directories in all subdirectories, including the"
                            + " subdirectories created later. If false, only the events of the entries directly in the"
                            + " directory are received, and the files of the subdirectories are not hashed. The subdirectories"
                            + " are not watched either, except where the watch service always watches the whole directory"
                            + " tree, such as on macOS.",
              defaultValue = "true")
    private boolean recursive = true;
    @UriParam(label = "advanced",
              description = "The number of threads that take the events from the queue and route them. Increase it when the"
                            + " route is slower than the events arrive. With more than one thread, the events can be routed in"
                            + " another order than they occurred.",
              defaultValue = "1")
    private int concurrentConsumers = 1;
    @UriParam(label = "advanced",
              description = "The number of threads that read the events from the watch service of the directory and put them in"
                            + " the queue. Increase it when the log warns that an OVERFLOW occurred, which means that the file"
                            + " system dropped events. All the threads read the same watch service, and each one registers the"
                            + " watched directories again when the consumer starts (and hashes their files again when"
                            + " useFileHashing is enabled), so on a large tree a higher value makes the start slower. More"
                            + " threads can only help when the events come from several directories at the same time, as the"
                            + " events of one directory are read by one thread at a time.",
              defaultValue = "1")
    private int pollThreads = 1;
    @UriParam(description = "An Ant-style pattern, such as **/*.txt, that the path of the file or directory relative to the"
                            + " path option must match, with / as separator and no leading /. The events of the paths that do"
                            + " not match are dropped, and an empty pattern matches all paths.",
              defaultValue = "**")
    private String antInclude;
    @UriParam(label = "advanced",
              description = "The maximum number of events in the queue between the pollThreads threads and the"
                            + " concurrentConsumers threads. When the queue is full, new events are dropped and a warning is"
                            + " logged once. A value of 0 or less means no limit.",
              defaultValue = "" + Integer.MAX_VALUE)
    private int queueSize = Integer.MAX_VALUE;
    @UriParam(label = "advanced",
              description = "The io.methvin.watcher.hashing.FileHasher that hashes each file to drop duplicate events, such as"
                            + " a MODIFY event of a file whose hash did not change. The default #murmur3FFileHasher hashes the"
                            + " content, and so reads every file in the directory tree in full when the consumer starts;"
                            + " #lastModifiedTimeFileHasher uses the last modified time instead, which suits large files. Not"
                            + " used when useFileHashing is false.",
              defaultValue = "#murmur3FFileHasher")
    private FileHasher fileHasher;
    @UriParam(description = "Whether to hash the files with the fileHasher to drop duplicate events, as some platforms and JDKs"
                            + " report a change more than once. If false, no file is hashed, and the route can receive the same"
                            + " event more than once.",
              defaultValue = "true")
    private boolean useFileHashing = true;

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
