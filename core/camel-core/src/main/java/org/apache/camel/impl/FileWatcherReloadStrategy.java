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
package org.apache.camel.impl;

import java.io.File;
import java.io.InputStream;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.processor.UnitOfWorkProducer;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.IOHelper;

/**
 * A file based {@link org.apache.camel.spi.ReloadStrategy} which watches a file folder
 * for modified files and reload on file changes.
 */
@ManagedResource(description = "Managed FileWatcherReloadStrategy")
public class FileWatcherReloadStrategy extends ReloadStrategySupport {

    private String folder;
    private boolean isRecursive;
    private long delay = 500;
    private Processor task;
    private Endpoint endpoint;
    private Consumer consumer;

    public FileWatcherReloadStrategy() {
        setRecursive(false);
    }

    public FileWatcherReloadStrategy(String directory) {
        setFolder(directory);
        setRecursive(false);
    }
    
    public FileWatcherReloadStrategy(String directory, boolean isRecursive) {
        setFolder(directory);
        setRecursive(isRecursive);
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }
    
    public void setRecursive(boolean isRecursive) {
        this.isRecursive = isRecursive;
    }

    /**
     * Sets the delay in millis between checks for updated files.
     * The default is 500 millis.
     */
    public void setDelay(long delay) {
        this.delay = delay;
    }

    @ManagedAttribute(description = "Folder being watched")
    public String getFolder() {
        return folder;
    }
    
    @ManagedAttribute(description = "Whether the reload strategy watches directory recursively")
    public boolean isRecursive() {
        return isRecursive;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (folder == null) {
            // no folder configured
            return;
        }

        File dir = new File(folder);
        if (dir.exists() && dir.isDirectory()) {
            log.info("Starting ReloadStrategy to watch directory: {}", dir);

            // only include xml files
            endpoint = getCamelContext().getEndpoint("file:" + dir + "?delay=" + delay + "&recursive=" + isRecursive
                + "&include=.*xml$&readLock=none&noop=true&idempotentKey=${file:name}-${file:modified}");
            // must wrap in unit of work
            task = new UnitOfWorkProducer(new UpdatedFileProcessor(endpoint));
            consumer = endpoint.createConsumer(task);

            ServiceHelper.startService(endpoint);
            ServiceHelper.startService(consumer);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (consumer != null) {
            ServiceHelper.stopAndShutdownServices(consumer);
            getCamelContext().removeService(consumer);
        }
        if (endpoint != null) {
            ServiceHelper.stopAndShutdownServices(endpoint);
            getCamelContext().removeEndpoint(endpoint);
        }
    }

    private final class UpdatedFileProcessor extends DefaultProducer {

        private UpdatedFileProcessor(Endpoint endpoint) {
            super(endpoint);
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            String name = exchange.getMessage().getHeader(Exchange.FILE_NAME, String.class);
            log.debug("Modified/Created file: {}", name);

            try {
                InputStream is = exchange.getMessage().getMandatoryBody(InputStream.class);
                onReloadXml(getCamelContext(), name, is);
                IOHelper.close(is);
            } catch (Exception e) {
                log.warn("Error reloading routes from file: " + name + " due " + e.getMessage() + ". This exception is ignored.", e);
            }
        }
    }

}
