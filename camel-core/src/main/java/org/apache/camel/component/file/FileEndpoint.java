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
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Properties;

import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.file.strategy.FileProcessStrategySupport;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.util.FactoryFinder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.UuidGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A <a href="http://activemq.apache.org/camel/file.html">File Endpoint</a> for
 * working with file systems
 *
 * @version $Revision$
 */
public class FileEndpoint extends ScheduledPollEndpoint<FileExchange> {
    private static final transient Log LOG = LogFactory.getLog(FileEndpoint.class);
    private static final String DEFAULT_STRATEGYFACTORY_CLASS =
        "org.apache.camel.component.file.strategy.FileProcessStrategyFactory";

    private File file;
    private FileProcessStrategy fileProcessStrategy;
    private boolean autoCreate = true;
    private boolean lock = true;
    private boolean delete;
    private boolean noop;
    private boolean append = true;
    private String moveNamePrefix;
    private String moveNamePostfix;
    private String[] excludedNamePrefixes = {"."};
    private String[] excludedNamePostfixes = {FileProcessStrategySupport.DEFAULT_LOCK_FILE_POSTFIX};
    private int bufferSize = 128 * 1024;
    private boolean ignoreFileNameHeader;

    protected FileEndpoint(File file, String endpointUri, FileComponent component) {
        super(endpointUri, component);
        this.file = file;
    }

    public FileEndpoint(String endpointUri, File file) {
        super(endpointUri);
        this.file = file;
    }

    public FileEndpoint(File file) {
        this.file = file;
    }

    public FileEndpoint() {
    }

    public Producer<FileExchange> createProducer() throws Exception {
        Producer<FileExchange> result = new FileProducer(this);
        return result;
    }

    public Consumer<FileExchange> createConsumer(Processor processor) throws Exception {
        Consumer<FileExchange> result = new FileConsumer(this, processor);
        configureConsumer(result);
        return result;
    }

    /**
     * Create a new exchange for communicating with this endpoint
     *
     * @param file  the file
     * @return the created exchange
     */
    public FileExchange createExchange(File file) {
        return new FileExchange(getCamelContext(), getExchangePattern(), file);
    }

    @Override
    public FileExchange createExchange() {
        return createExchange(getFile());
    }

    @Override
    public FileExchange createExchange(ExchangePattern pattern) {
        return new FileExchange(getCamelContext(), pattern, file);
    }


    /**
     * Return the file name that will be auto-generated for the given message if none is provided
     */
    public String getGeneratedFileName(Message message) {
        return getFileFriendlyMessageId(message.getMessageId());
    }

    /**
     * Configures the given message with the file which sets the body to the file object
     * and sets the {@link FileComponent#HEADER_FILE_NAME} header.
     */
    public void configureMessage(File file, Message message) {
        message.setBody(file);
        String relativePath = file.getPath().substring(getFile().getPath().length());
        if (relativePath.startsWith(File.separator) || relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        message.setHeader(FileComponent.HEADER_FILE_NAME, relativePath);
    }

    public File getFile() {
        ObjectHelper.notNull(file, "file");
        if (autoCreate && !file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public boolean isSingleton() {
        return true;
    }

    public boolean isAutoCreate() {
        return this.autoCreate;
    }

    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    public FileProcessStrategy getFileStrategy() {
        if (fileProcessStrategy == null) {
            fileProcessStrategy = createFileStrategy();
            LOG.debug("Using file process strategy: " + fileProcessStrategy);
        }
        return fileProcessStrategy;
    }

    /**
     * Sets the strategy to be used when the file has been processed such as
     * deleting or renaming it etc.
     *
     * @param fileProcessStrategy the new strategy to use
     */
    public void setFileStrategy(FileProcessStrategy fileProcessStrategy) {
        this.fileProcessStrategy = fileProcessStrategy;
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
     * Sets the name postfix appended to moved files. For example to rename all
     * the files from <tt>*</tt> to <tt>*.done</tt> set this value to <tt>.done</tt>
     */
    public void setMoveNamePostfix(String moveNamePostfix) {
        this.moveNamePostfix = moveNamePostfix;
    }

    public String getMoveNamePrefix() {
        return moveNamePrefix;
    }

    /**
     * Sets the name prefix appended to moved files. For example to move
     * processed files into a hidden directory called <tt>.camel</tt> set this value to
     * <tt>.camel/</tt>
     */
    public void setMoveNamePrefix(String moveNamePrefix) {
        this.moveNamePrefix = moveNamePrefix;
    }

    public String[] getExcludedNamePrefixes() {
        return excludedNamePrefixes;
    }

    /**
     * Sets the excluded file name prefixes, such as <tt>"."</tt> for hidden files which
     * are excluded by default
     */
    public void setExcludedNamePrefixes(String[] excludedNamePrefixes) {
        this.excludedNamePrefixes = excludedNamePrefixes;
    }

    public String[] getExcludedNamePostfixes() {
        return excludedNamePostfixes;
    }

    /**
     * Sets the excluded file name postfixes, such as {@link FileProcessStrategySupport#DEFAULT_LOCK_FILE_POSTFIX}
     * to ignore lock files by default.
     */
    public void setExcludedNamePostfixes(String[] excludedNamePostfixes) {
        this.excludedNamePostfixes = excludedNamePostfixes;
    }

    public boolean isNoop() {
        return noop;
    }

    /**
     * If set to true then the default {@link FileProcessStrategy} will be to use the
     * {@link org.apache.camel.component.file.strategy.NoOpFileProcessStrategy NoOpFileProcessStrategy}
     * to not move or copy processed files
     */
    public void setNoop(boolean noop) {
        this.noop = noop;
    }

    public boolean isAppend() {
        return append;
    }

    /**
     * When writing do we append to the end of the file, or replace it?
     * The default is to append
     */
    public void setAppend(boolean append) {
        this.append = append;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Sets the buffer size used to read/write files
     */
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public boolean isIgnoreFileNameHeader() {
        return ignoreFileNameHeader;
    }

    /**
     * If this flag is enabled then producers will ignore the {@link FileComponent#HEADER_FILE_NAME}
     * header and generate a new dynamic file
     */
    public void setIgnoreFileNameHeader(boolean ignoreFileNameHeader) {
        this.ignoreFileNameHeader = ignoreFileNameHeader;
    }

    /**
     * A strategy method to lazily create the file strategy
     */
    protected FileProcessStrategy createFileStrategy() {
        Class<?> factory = null;
        try {
            FactoryFinder finder = new FactoryFinder("META-INF/services/org/apache/camel/component/");
            factory = finder.findClass("file", "strategy.factory.");
        } catch (ClassNotFoundException e) {
            LOG.debug("'strategy.factory.class' not found", e);
        } catch (IOException e) {
            LOG.debug("No strategy factory defined in 'META-INF/services/org/apache/camel/component/file'", e);
        }

        if (factory == null) {
            // use default
            factory = ObjectHelper.loadClass(DEFAULT_STRATEGYFACTORY_CLASS);
            if (factory == null) {
                throw new TypeNotPresentException("FileProcessStrategyFactory class not found", null);
            }
        }

        try {
            Method factoryMethod = factory.getMethod("createFileProcessStrategy", Properties.class);
            return (FileProcessStrategy) ObjectHelper.invokeMethod(factoryMethod, null, getParamsAsProperties());
        } catch (NoSuchMethodException e) {
            throw new TypeNotPresentException(factory.getSimpleName()
                + ".createFileProcessStrategy(Properties params) method not found", e);
        }
    }

    protected Properties getParamsAsProperties() {
        Properties params = new Properties();
        if (isNoop()) {
            params.setProperty("noop", Boolean.toString(true));
        }
        if (isDelete()) {
            params.setProperty("delete", Boolean.toString(true));
        }
        if (isAppend()) {
            params.setProperty("append", Boolean.toString(true));
        }
        if (isLock()) {
            params.setProperty("lock", Boolean.toString(true));
        }
        if (moveNamePrefix != null) {
            params.setProperty("moveNamePrefix", moveNamePrefix);
        }
        if (moveNamePostfix != null) {
            params.setProperty("moveNamePostfix", moveNamePostfix);
        }
        return params;
    }

    @Override
    protected String createEndpointUri() {
        return "file://" + getFile().getAbsolutePath();
    }
    
    protected  String getFileFriendlyMessageId(String id) {
        return UuidGenerator.generateSanitizedId(id);
    }
}
