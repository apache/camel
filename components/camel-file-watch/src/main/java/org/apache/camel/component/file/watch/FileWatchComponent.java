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
    public static final String EVENT_TYPE_HEADER = "CamelFileEventType";

    @Metadata(label = "consumer",
    description = "The number of concurrent consumers. Increase this value, if your route is slow to prevent buffering in queue.",
    defaultValue = "1")
    private int concurrentConsumers = 1;

    @Metadata(label = "consumer", description = "Maximum size of queue between WatchService and consumer. Unbounded by default.",
    defaultValue = "" + Integer.MAX_VALUE)
    private int queueSize = Integer.MAX_VALUE;

    @Metadata(label = "consumer",
    description = "The number of threads polling WatchService. Increase this value, if you see OVERFLOW messages in log.",
    defaultValue = "1")
    private int pollThreads = 1;

    @Metadata(label = "consumer",
    description = "Reference to io.methvin.watcher.hashing.FileHasher. "
    + "This prevents emitting duplicate events on some platforms. "
    + "For working with large files and if you dont need detect multiple modifications per second per file, "
    + "use #lastModifiedTimeFileHasher. You can also provide custom implementation in registry.",
    defaultValue = "#murmur3FFileHasher")
    private FileHasher fileHasher = FileHasher.DEFAULT_FILE_HASHER;

    @Metadata(label = "consumer",
    description = "Enables or disables file hashing to detect duplicate events. "
    + "If you disable this, you can get some events multiple times on some platforms and JDKs. "
    + "Check java.nio.file.WatchService limitations for your target platform.",
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

        // CAMEL-13954: Due to the auto generated property configurator, this intends to set it manually instead of relying on the auto generated property configurator
        if (parameters.containsKey("events")) {
            endpoint.setEvents(parameters.get("events").toString());
            parameters.remove("events");
        }

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
