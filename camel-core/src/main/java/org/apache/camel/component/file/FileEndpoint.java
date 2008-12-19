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
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.language.simple.FileLanguage;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.spi.IdempotentRepository;
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
public class FileEndpoint extends ScheduledPollEndpoint {
    public static final transient String DEFAULT_LOCK_FILE_POSTFIX = ".camelLock";

    private static final transient Log LOG = LogFactory.getLog(FileEndpoint.class);
    private static final transient String DEFAULT_STRATEGYFACTORY_CLASS =
        "org.apache.camel.component.file.strategy.FileProcessStrategyFactory";
    private static final transient int DEFAULT_IDEMPOTENT_CACHE_SIZE = 1000;

    private File file;
    private FileProcessStrategy fileProcessStrategy;
    private boolean autoCreate = true;
    private boolean lock = true;
    private boolean delete;
    private boolean noop;
    private boolean append = true;
    private String moveNamePrefix;
    private String moveNamePostfix;
    private String preMoveNamePrefix;
    private String preMoveNamePostfix;
    private String excludedNamePrefix;
    private String excludedNamePostfix;
    private int bufferSize = 128 * 1024;
    private boolean ignoreFileNameHeader;
    private Expression expression;
    private Expression preMoveExpression;
    private String tempPrefix;
    private boolean idempotent;
    private IdempotentRepository idempotentRepository;
    private FileFilter filter;
    private Comparator<File> sorter;
    private Comparator<FileExchange> sortBy;
    private ExclusiveReadLockStrategy exclusiveReadLockStrategy;
    private String readLock = "fileLock";
    private long readLockTimeout;

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

    public Producer createProducer() throws Exception {
        return new FileProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer result = new FileConsumer(this, processor);

        if (isDelete() && (getMoveNamePrefix() != null || getMoveNamePostfix() != null || getExpression() != null)) {
            throw new IllegalArgumentException("You cannot set delet and a moveNamePrefix, moveNamePostfix or expression option");
        }
        
        // if noop=true then idempotent should also be configured
        if (isNoop() && !isIdempotent()) {
            LOG.info("Endpoint is configured with noop=true so forcing endpoint to be idempotent as well");
            setIdempotent(true);
        }

        // if idempotent and no repository set then create a default one
        if (isIdempotent() && idempotentRepository == null) {
            LOG.info("Using default memory based idempotent repository with cache max size: " + DEFAULT_IDEMPOTENT_CACHE_SIZE);
            idempotentRepository = MemoryIdempotentRepository.memoryIdempotentRepository(DEFAULT_IDEMPOTENT_CACHE_SIZE);
        }

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
    public Exchange createExchange() {
        return createExchange(getFile());
    }

    @Override
    public Exchange createExchange(ExchangePattern pattern) {
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

    public String getPreMoveNamePrefix() {
        return preMoveNamePrefix;
    }

    public void setPreMoveNamePrefix(String preMoveNamePrefix) {
        this.preMoveNamePrefix = preMoveNamePrefix;
    }

    /**
     * Sets the name prefix appended to pre moved files. For example to move
     * files before processing into a inprogress directory called <tt>.inprogress</tt> set this value to
     * <tt>.inprogress/</tt>
     */
    public String getPreMoveNamePostfix() {
        return preMoveNamePostfix;
    }

    /**
     * Sets the name postfix appended to pre moved files. For example to rename
     * files before processing from <tt>*</tt> to <tt>*.inprogress</tt> set this value to <tt>.inprogress</tt>
     */
    public void setPreMoveNamePostfix(String preMoveNamePostfix) {
        this.preMoveNamePostfix = preMoveNamePostfix;
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

    public String getExcludedNamePrefix() {
        return excludedNamePrefix;
    }

    public void setExcludedNamePrefix(String excludedNamePrefix) {
        this.excludedNamePrefix = excludedNamePrefix;
    }

    public String getExcludedNamePostfix() {
        return excludedNamePostfix;
    }

    public void setExcludedNamePostfix(String excludedNamePostfix) {
        this.excludedNamePostfix = excludedNamePostfix;
    }

    public Expression getExpression() {
        return expression;
    }

    public void setExpression(Expression expression) {
        this.expression = expression;
    }

    /**
     * Sets the expression based on {@link FileLanguage}
     */
    public void setExpression(String fileLanguageExpression) {
        this.expression = FileLanguage.file(fileLanguageExpression);
    }

    public Expression getPreMoveExpression() {
        return preMoveExpression;
    }

    public void setPreMoveExpression(Expression expression) {
        this.preMoveExpression = expression;
    }

    /**
     * Sets the pre move expression based on {@link FileLanguage}
     */
    public void setPreMoveExpression(String fileLanguageExpression) {
        this.preMoveExpression = FileLanguage.file(fileLanguageExpression);
    }

    public String getTempPrefix() {
        return tempPrefix;
    }

    /**
     * Enables and uses temporary prefix when writing files, after write it will be renamed to the correct name.
     */
    public void setTempPrefix(String tempPrefix) {
        this.tempPrefix = tempPrefix;
    }

    public boolean isIdempotent() {
        return idempotent;
    }

    public void setIdempotent(boolean idempotent) {
        this.idempotent = idempotent;
    }

    public IdempotentRepository getIdempotentRepository() {
        return idempotentRepository;
    }

    public void setIdempotentRepository(IdempotentRepository idempotentRepository) {
        this.idempotentRepository = idempotentRepository;
    }

    public FileFilter getFilter() {
        return filter;
    }

    public void setFilter(FileFilter filter) {
        this.filter = filter;
    }

    public Comparator<File> getSorter() {
        return sorter;
    }

    public void setSorter(Comparator<File> sorter) {
        this.sorter = sorter;
    }

    public Comparator<FileExchange> getSortBy() {
        return sortBy;
    }

    public void setSortBy(Comparator<FileExchange> sortBy) {
        this.sortBy = sortBy;
    }

    public void setSortBy(String expression) {
        setSortBy(expression, false);
    }

    public void setSortBy(String expression, boolean reverse) {
        setSortBy(DefaultFileSorter.sortByFileLanguage(expression, reverse));
    }

    public ExclusiveReadLockStrategy getExclusiveReadLockStrategy() {
        return exclusiveReadLockStrategy;
    }

    public void setExclusiveReadLockStrategy(ExclusiveReadLockStrategy exclusiveReadLockStrategy) {
        this.exclusiveReadLockStrategy = exclusiveReadLockStrategy;
    }

    public String getReadLock() {
        return readLock;
    }

    public void setReadLock(String readLock) {
        this.readLock = readLock;
    }

    public long getReadLockTimeout() {
        return readLockTimeout;
    }

    public void setReadLockTimeout(long readLockTimeout) {
        this.readLockTimeout = readLockTimeout;
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
            Method factoryMethod = factory.getMethod("createFileProcessStrategy", Map.class);
            return (FileProcessStrategy) ObjectHelper.invokeMethod(factoryMethod, null, getParamsAsMap());
        } catch (NoSuchMethodException e) {
            throw new TypeNotPresentException(factory.getSimpleName()
                + ".createFileProcessStrategy(Properties params) method not found", e);
        }
    }

    protected Map<String, Object> getParamsAsMap() {
        Map<String, Object> params = new HashMap<String, Object>();

        if (isNoop()) {
            params.put("noop", Boolean.toString(true));
        }
        if (isDelete()) {
            params.put("delete", Boolean.toString(true));
        }
        if (isAppend()) {
            params.put("append", Boolean.toString(true));
        }
        if (isLock()) {
            params.put("lock", Boolean.toString(true));
        }
        if (moveNamePrefix != null) {
            params.put("moveNamePrefix", moveNamePrefix);
        }
        if (moveNamePostfix != null) {
            params.put("moveNamePostfix", moveNamePostfix);
        }
        if (preMoveNamePrefix != null) {
            params.put("preMoveNamePrefix", preMoveNamePrefix);
        }
        if (preMoveNamePostfix != null) {
            params.put("preMoveNamePostfix", preMoveNamePostfix);
        }
        if (expression != null) {
            params.put("expression", expression);
        }
        if (preMoveExpression != null) {
            params.put("preMoveExpression", preMoveExpression);
        }
        if (exclusiveReadLockStrategy != null) {
            params.put("exclusiveReadLockStrategy", exclusiveReadLockStrategy);
        }
        if (readLock != null) {
            params.put("readLock", readLock);
        }
        if (readLockTimeout > 0) {
            params.put("readLockTimeout", Long.valueOf(readLockTimeout));
        }

        return params;
    }

    @Override
    protected String createEndpointUri() {
        return "file://" + getFile().getAbsolutePath();
    }

    protected String getFileFriendlyMessageId(String id) {
        return UuidGenerator.generateSanitizedId(id);
    }
}
