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

import java.util.Map;

import io.methvin.watcher.hashing.FileHasher;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Represents the component that manages {@link FileWatchEndpoint}.
 */
@Component("file-watch")
public class FileWatchComponent extends DefaultComponent {

    @Metadata(label = "advanced",
              description = "The number of threads that take the events from the queue and route them. Increase it when the"
                            + " route is slower than the events arrive. With more than one thread, the events can be routed in"
                            + " another order than they occurred.",
              defaultValue = "1")
    private int concurrentConsumers = 1;

    @Metadata(label = "advanced",
              description = "The maximum number of events in the queue between the pollThreads threads and the"
                            + " concurrentConsumers threads. When the queue is full, new events are dropped and a warning is"
                            + " logged once. A value of 0 or less means no limit.",
              defaultValue = "" + Integer.MAX_VALUE)
    private int queueSize = Integer.MAX_VALUE;

    @Metadata(label = "advanced",
              description = "The number of threads that read the events from the watch service of the directory and put them in"
                            + " the queue. Increase it when the log warns that an OVERFLOW occurred, which means that the file"
                            + " system dropped events. All the threads read the same watch service, and each one registers the"
                            + " watched directories again when the consumer starts (and hashes their files again when"
                            + " useFileHashing is enabled), so on a large tree a higher value makes the start slower. More"
                            + " threads can only help when the events come from several directories at the same time, as the"
                            + " events of one directory are read by one thread at a time.",
              defaultValue = "1")
    private int pollThreads = 1;

    @Metadata(label = "advanced",
              description = "The io.methvin.watcher.hashing.FileHasher that hashes each file to drop duplicate events, such as"
                            + " a MODIFY event of a file whose hash did not change. The default #murmur3FFileHasher hashes the"
                            + " content, and so reads every file in the directory tree in full when the consumer starts;"
                            + " #lastModifiedTimeFileHasher uses the last modified time instead, which suits large files. Not"
                            + " used when useFileHashing is false.",
              defaultValue = "#murmur3FFileHasher")
    private FileHasher fileHasher = FileHasher.DEFAULT_FILE_HASHER;

    @Metadata(description = "Whether to hash the files with the fileHasher to drop duplicate events, as some platforms and JDKs"
                            + " report a change more than once. If false, no file is hashed, and the route can receive the same"
                            + " event more than once.",
              defaultValue = "true")
    private boolean useFileHashing = true;

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getPollThreads() {
        return pollThreads;
    }

    public void setPollThreads(int pollThreads) {
        this.pollThreads = pollThreads;
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
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        FileWatchEndpoint endpoint = new FileWatchEndpoint(uri, remaining, this);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    /**
     * Register common FileHashers to registry, to make this available out-of-box to use in endpoint definition.
     */
    @Override
    protected void doInit() throws Exception {
        super.doInit();
        Registry registry = getCamelContext().getRegistry();
        registry.bind("murmur3FFileHasher", FileHasher.class, FileHasher.DEFAULT_FILE_HASHER);
        registry.bind("lastModifiedTimeFileHasher", FileHasher.class, FileHasher.LAST_MODIFIED_TIME);
    }
}
