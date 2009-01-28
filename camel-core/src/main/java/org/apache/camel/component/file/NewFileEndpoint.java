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
 *
 */
public class NewFileEndpoint extends GenericFileEndpoint<File> {

    private NewFileOperations operations = new NewFileOperations(this);
    private File file;

    public NewFileEndpoint() {
        // use marker file as default exclusive read locks
        this.readLock = "markerFile";
    }

    public NewFileEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
        // use marker file as default exclusive read locks
        this.readLock = "markerFile";
    }

    public NewFileConsumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(operations, "operations");
        ObjectHelper.notNull(file, "file");

        NewFileConsumer result = new NewFileConsumer(this, processor, operations);

        if (isDelete() && (getMoveNamePrefix() != null || getMoveNamePostfix() != null || getExpression() != null)) {
            throw new IllegalArgumentException("You cannot set delete=true and a moveNamePrefix, moveNamePostfix or expression option");
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

        configureConsumer(result);
        return result;
    }

    public GenericFileProducer<File> createProducer() throws Exception {
        ObjectHelper.notNull(operations, "operations");
        return new GenericFileProducer<File>(this, operations);
    }

    public GenericFileExchange<File> createExchange(GenericFile<File> file) {
        GenericFileExchange<File> exchange = new GenericFileExchange<File>(getCamelContext());
        exchange.setGenericFile(file);
        return exchange;
    }

    public GenericFileExchange createExchange() {
        return new GenericFileExchange(getCamelContext());
    }

    public NewFileOperations getOperations() {
        return operations;
    }

    public void setOperations(NewFileOperations operations) {
        this.operations = operations;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
        // update configuration as well
        getConfiguration().setFile(file.getPath());
    }

    @Override
    public String getScheme() {
        // TODO change to file when this is ready
        return "newfile";
    }

    @Override
    protected String createEndpointUri() {
        return "file://" + getFile().getAbsolutePath();
    }

}
