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
package org.apache.camel.component.file;

import java.io.File;

import org.apache.camel.Component;
import org.apache.camel.Processor;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.util.ObjectHelper;

/**
 * File endpoint.
 */
public class FileEndpoint extends GenericFileEndpoint<File> {

    private FileOperations operations = new FileOperations(this);
    private File file;

    public FileEndpoint() {
        // use marker file as default exclusive read locks
        this.readLock = "markerFile";
    }

    public FileEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
        // use marker file as default exclusive read locks
        this.readLock = "markerFile";
    }

    public FileConsumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(operations, "operations");
        ObjectHelper.notNull(file, "file");

        // we assume its a file if the name has a dot in it (eg foo.txt)
        if (file.getName().contains(".")) {
            throw new IllegalArgumentException("Only directory is supported. Endpoint must be configured with a valid starting directory: " + file);
        }

        // auto create starting directory if needed
        if (!file.exists() && !file.isDirectory()) {
            if (isAutoCreate()) {
                if (log.isDebugEnabled()) {
                    log.debug("Creating non existing starting directory: " + file);
                }
                boolean absolute = file.isAbsolute();
                operations.buildDirectory(file.getPath(), absolute);
            }
        }

        FileConsumer result = new FileConsumer(this, processor, operations);

        if (isDelete() && getMove() != null) {
            throw new IllegalArgumentException("You cannot set both delete=true and move options");
        }

        // if noop=true then idempotent should also be configured
        if (isNoop() && !isIdempotent()) {
            log.info("Endpoint is configured with noop=true so forcing endpoint to be idempotent as well");
            setIdempotent(true);
        }

        // if idempotent and no repository set then create a default one
        if (isIdempotent() && idempotentRepository == null) {
            log.info("Using default memory based idempotent repository with cache max size: " + DEFAULT_IDEMPOTENT_CACHE_SIZE);
            idempotentRepository = MemoryIdempotentRepository.memoryIdempotentRepository(DEFAULT_IDEMPOTENT_CACHE_SIZE);
        }

        // set max messages per poll
        result.setMaxMessagesPerPoll(getMaxMessagesPerPoll());

        configureConsumer(result);
        return result;
    }

    public GenericFileProducer<File> createProducer() throws Exception {
        ObjectHelper.notNull(operations, "operations");
        return new GenericFileProducer<File>(this, operations);
    }

    public GenericFileExchange<File> createExchange(GenericFile<File> file) {
        GenericFileExchange<File> exchange = new GenericFileExchange<File>(this);
        if (file != null) {
            file.bindToExchange(exchange);
        }
        return exchange;
    }

    public GenericFileExchange createExchange() {
        return new GenericFileExchange(this);
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
        // update configuration as well
        getConfiguration().setDirectory(file.getPath());
    }

    @Override
    public String getScheme() {
        return "file";
    }

    @Override
    protected String createEndpointUri() {
        return "file://" + getFile().getAbsolutePath();
    }

    @Override
    public char getFileSeparator() {       
        return File.separatorChar;
    }

    @Override
    public boolean isAbsolute(String name) {
        // relative or absolute path?
        File file = new File(name);
        return file.isAbsolute();
    }

}
