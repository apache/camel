/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.file.strategy.DeleteFileStrategy;
import org.apache.camel.component.file.strategy.FileStrategy;
import org.apache.camel.component.file.strategy.NoOpFileStrategy;
import org.apache.camel.component.file.strategy.RenameFileStrategy;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;

/**
 * A <a href="http://activemq.apache.org/camel/file.html">File Endpoint</a> for working with file systems
 *
 * @version $Revision: 523016 $
 */
public class FileEndpoint extends ScheduledPollEndpoint<FileExchange> {
    private static final transient Log log = LogFactory.getLog(FileEndpoint.class);
    private File file;
    private FileStrategy fileStrategy;
    private boolean autoCreate = true;
    private boolean lock = true;
    private boolean delete = false;
    private boolean noop = false;
    private String moveNamePrefix = null;
    private String moveNamePostfix = null;
    private String[] excludedNamePrefixes = {"."};

    protected FileEndpoint(File file, String endpointUri, FileComponent component) {
        super(endpointUri, component);
        this.file = file;
    }

    /**
     * @return a Producer
     * @throws Exception
     * @see org.apache.camel.Endpoint#createProducer()
     */
    public Producer<FileExchange> createProducer() throws Exception {
        Producer<FileExchange> result = new FileProducer(this);
        return result;
    }

    /**
     * @param file
     * @return a Consumer
     * @throws Exception
     * @see org.apache.camel.Endpoint#createConsumer(org.apache.camel.Processor)
     */
    public Consumer<FileExchange> createConsumer(Processor file) throws Exception {
        Consumer<FileExchange> result = new FileConsumer(this, file);
        configureConsumer(result);
        return result;
    }

    /**
     * @param file
     * @return a FileExchange
     * @see org.apache.camel.Endpoint#createExchange()
     */
    public FileExchange createExchange(File file) {
        return new FileExchange(getContext(), file);
    }

    /**
     * @return an Exchange
     * @see org.apache.camel.Endpoint#createExchange()
     */
    public FileExchange createExchange() {
        return createExchange(getFile());
    }

    public File getFile() {
        if (autoCreate && !file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    public boolean isSingleton() {
        return true;
    }

    /**
     * @return the autoCreate
     */
    public boolean isAutoCreate() {
        return this.autoCreate;
    }

    /**
     * @param autoCreate the autoCreate to set
     */
    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    public FileStrategy getFileStrategy() {
        if (fileStrategy == null) {
            fileStrategy = createFileStrategy();
            log.debug("" + this + " using strategy: " + fileStrategy);
        }
        return fileStrategy;
    }

    /**
     * Sets the strategy to be used when the file has been processed
     * such as deleting or renaming it etc.
     *
     * @param fileStrategy the new stategy to use
     */
    public void setFileStrategy(FileStrategy fileStrategy) {
        this.fileStrategy = fileStrategy;
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public boolean isLock() {
        return lock;
    }

    public void setLock(boolean lock) {
        this.lock = lock;
    }

    public String getMoveNamePostfix() {
        return moveNamePostfix;
    }

    /**
     * Sets the name postfix appended to moved files. For example
     * to rename all the files from * to *.done set this value to ".done"
     *
     * @param moveNamePostfix
     * @see RenameFileStrategy#setNamePostfix(String)
     */
    public void setMoveNamePostfix(String moveNamePostfix) {
        this.moveNamePostfix = moveNamePostfix;
    }

    public String getMoveNamePrefix() {
        return moveNamePrefix;
    }

    /**
     * Sets the name prefix appended to moved files. For example
     * to move processed files into a hidden directory called ".camel"
     * set this value to ".camel/"
     *
     * @see RenameFileStrategy#setNamePrefix(String)
     */
    public void setMoveNamePrefix(String moveNamePrefix) {
        this.moveNamePrefix = moveNamePrefix;
    }

    public String[] getExcludedNamePrefixes() {
        return excludedNamePrefixes;
    }

    /**
     * Sets the excluded file name prefixes, such as "." for hidden files
     * which are excluded by default
     */
    public void setExcludedNamePrefixes(String[] excludedNamePrefixes) {
        this.excludedNamePrefixes = excludedNamePrefixes;
    }

    public boolean isNoop() {
        return noop;
    }

    /**
     * If set to true then the default {@link FileStrategy} will be to use
     * the {@link NoOpFileStrategy} to not move or copy processed files
     *
     * @param noop
     */
    public void setNoop(boolean noop) {
        this.noop = noop;
    }

    /**
     * A strategy method to lazily create the file strategy
     */
    protected FileStrategy createFileStrategy() {
        if (isNoop()) {
            return new NoOpFileStrategy();
        }
        else if (moveNamePostfix != null || moveNamePrefix != null) {
            if (isDelete()) {
                throw new IllegalArgumentException("You cannot set the deleteFiles property and a moveFilenamePostfix or moveFilenamePrefix");
            }
            return new RenameFileStrategy(isLock(), moveNamePrefix, moveNamePostfix);
        }
        else if (isDelete()) {
            return new DeleteFileStrategy(isLock());
        }
        else {
            return new RenameFileStrategy(isLock());
        }
    }
}
