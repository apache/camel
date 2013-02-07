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

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.Language;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for file endpoints
 */
public abstract class GenericFileEndpoint<T> extends ScheduledPollEndpoint implements BrowsableEndpoint {

    protected static final transient String DEFAULT_STRATEGYFACTORY_CLASS = "org.apache.camel.component.file.strategy.GenericFileProcessStrategyFactory";
    protected static final transient int DEFAULT_IDEMPOTENT_CACHE_SIZE = 1000;

    protected final transient Logger log = LoggerFactory.getLogger(getClass());

    protected GenericFileProcessStrategy<T> processStrategy;
    protected GenericFileConfiguration configuration;

    protected IdempotentRepository<String> inProgressRepository = new MemoryIdempotentRepository();
    protected String localWorkDirectory;
    protected boolean autoCreate = true;
    protected boolean startingDirectoryMustExist;
    protected boolean directoryMustExist;
    protected int bufferSize = FileUtil.BUFFER_SIZE;
    protected GenericFileExist fileExist = GenericFileExist.Override;
    protected boolean noop;
    protected boolean recursive;
    protected boolean delete;
    protected boolean flatten;
    protected int maxMessagesPerPoll;
    protected boolean eagerMaxMessagesPerPoll = true;
    protected int maxDepth = Integer.MAX_VALUE;
    protected int minDepth;
    protected String tempPrefix;
    protected Expression tempFileName;
    protected boolean eagerDeleteTargetFile = true;
    protected String include;
    protected String exclude;
    protected String charset;
    protected Expression fileName;
    protected Expression move;
    protected Expression moveFailed;
    protected Expression preMove;
    protected Expression moveExisting;
    protected Boolean idempotent;
    protected Expression idempotentKey;
    protected IdempotentRepository<String> idempotentRepository;
    protected GenericFileFilter<T> filter;
    protected AntPathMatcherGenericFileFilter<T> antFilter;
    protected Comparator<GenericFile<T>> sorter;
    protected Comparator<Exchange> sortBy;
    protected String readLock = "none";
    protected long readLockCheckInterval = 1000;
    protected long readLockTimeout = 10000;
    protected long readLockMinLength = 1;
    protected GenericFileExclusiveReadLockStrategy<T> exclusiveReadLockStrategy;
    protected boolean keepLastModified;
    protected String doneFileName;
    protected boolean allowNullBody;

    public GenericFileEndpoint() {
    }

    public GenericFileEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public boolean isSingleton() {
        return true;
    }

    public abstract GenericFileConsumer<T> createConsumer(Processor processor) throws Exception;

    public abstract GenericFileProducer<T> createProducer() throws Exception;

    public abstract Exchange createExchange(GenericFile<T> file);

    public abstract String getScheme();

    public abstract char getFileSeparator();

    public abstract boolean isAbsolute(String name);

    /**
     * Return the file name that will be auto-generated for the given message if
     * none is provided
     */
    public String getGeneratedFileName(Message message) {
        return StringHelper.sanitize(message.getMessageId());
    }

    public GenericFileProcessStrategy<T> getGenericFileProcessStrategy() {
        if (processStrategy == null) {
            processStrategy = createGenericFileStrategy();
            log.debug("Using Generic file process strategy: {}", processStrategy);
        }
        return processStrategy;
    }

    /**
     * This implementation will <b>not</b> load the file content.
     * Any file locking is neither in use by this implementation..
     */
    @Override
    public List<Exchange> getExchanges() {
        final List<Exchange> answer = new ArrayList<Exchange>();

        GenericFileConsumer<?> consumer = null;
        try {
            // create a new consumer which can poll the exchanges we want to browse
            // do not provide a processor as we do some custom processing
            consumer = createConsumer(null);
            consumer.setCustomProcessor(new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                    answer.add(exchange);
                }
            });
            // do not start scheduler, as we invoke the poll manually
            consumer.setStartScheduler(false);
            // start consumer
            ServiceHelper.startService(consumer);
            // invoke poll which performs the custom processing, so we can browse the exchanges
            consumer.poll();
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        } finally {
            try {
                ServiceHelper.stopService(consumer);
            } catch (Exception e) {
                log.debug("Error stopping consumer used for browsing exchanges. This exception will be ignored", e);
            }
        }

        return answer;
    }

    /**
     * A strategy method to lazily create the file strategy
     */
    @SuppressWarnings("unchecked")
    protected GenericFileProcessStrategy<T> createGenericFileStrategy() {
        Class<?> factory = null;
        try {
            FactoryFinder finder = getCamelContext().getFactoryFinder("META-INF/services/org/apache/camel/component/");
            log.trace("Using FactoryFinder: {}", finder);
            factory = finder.findClass(getScheme(), "strategy.factory.", CamelContext.class);
        } catch (ClassNotFoundException e) {
            log.trace("'strategy.factory.class' not found", e);
        } catch (IOException e) {
            log.trace("No strategy factory defined in 'META-INF/services/org/apache/camel/component/'", e);
        }

        if (factory == null) {
            // use default
            try {
                log.trace("Using ClassResolver to resolve class: {}", DEFAULT_STRATEGYFACTORY_CLASS);
                factory = this.getCamelContext().getClassResolver().resolveClass(DEFAULT_STRATEGYFACTORY_CLASS);
            } catch (Exception e) {
                log.trace("Cannot load class: {}", DEFAULT_STRATEGYFACTORY_CLASS, e);
            }
            // fallback and us this class loader
            try {
                if (log.isTraceEnabled()) {
                    log.trace("Using classloader: {} to resolve class: {}", this.getClass().getClassLoader(), DEFAULT_STRATEGYFACTORY_CLASS);
                }
                factory = this.getCamelContext().getClassResolver().resolveClass(DEFAULT_STRATEGYFACTORY_CLASS, this.getClass().getClassLoader());
            } catch (Exception e) {
                if (log.isTraceEnabled()) {
                    log.trace("Cannot load class: {} using classloader: " + this.getClass().getClassLoader(), DEFAULT_STRATEGYFACTORY_CLASS, e);
                }
            }

            if (factory == null) {
                throw new TypeNotPresentException(DEFAULT_STRATEGYFACTORY_CLASS + " class not found", null);
            }
        }

        try {
            Method factoryMethod = factory.getMethod("createGenericFileProcessStrategy", CamelContext.class, Map.class);
            Map<String, Object> params = getParamsAsMap();
            log.debug("Parameters for Generic file process strategy {}", params);
            return (GenericFileProcessStrategy<T>) ObjectHelper.invokeMethod(factoryMethod, null, getCamelContext(), params);
        } catch (NoSuchMethodException e) {
            throw new TypeNotPresentException(factory.getSimpleName() + ".createGenericFileProcessStrategy method not found", e);
        }
    }

    public boolean isNoop() {
        return noop;
    }

    public void setNoop(boolean noop) {
        this.noop = noop;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public String getInclude() {
        return include;
    }

    public void setInclude(String include) {
        this.include = include;
    }

    public String getExclude() {
        return exclude;
    }

    public void setExclude(String exclude) {
        this.exclude = exclude;
    }

    public void setAntInclude(String antInclude) {
        if (this.antFilter == null) {
            this.antFilter = new AntPathMatcherGenericFileFilter<T>();
        }
        this.antFilter.setIncludes(antInclude);
    }

    public void setAntExclude(String antExclude) {
        if (this.antFilter == null) {
            this.antFilter = new AntPathMatcherGenericFileFilter<T>();
        }
        this.antFilter.setExcludes(antExclude);
    }

    /**
     * Sets case sensitive flag on {@link org.apache.camel.component.file.AntPathMatcherFileFilter}
     * <p/>
     * Is by default turned on <tt>true</tt>.
     */
    public void setAntFilterCaseSensitive(boolean antFilterCaseSensitive) {
        if (this.antFilter == null) {
            this.antFilter = new AntPathMatcherGenericFileFilter<T>();
        }
        this.antFilter.setCaseSensitive(antFilterCaseSensitive);
    }

    public GenericFileFilter<T> getAntFilter() {
        return antFilter;
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public boolean isFlatten() {
        return flatten;
    }

    public void setFlatten(boolean flatten) {
        this.flatten = flatten;
    }

    public Expression getMove() {
        return move;
    }

    public void setMove(Expression move) {
        this.move = move;
    }

    /**
     * Sets the move failure expression based on
     * {@link org.apache.camel.language.simple.SimpleLanguage}
     */
    public void setMoveFailed(String fileLanguageExpression) {
        String expression = configureMoveOrPreMoveExpression(fileLanguageExpression);
        this.moveFailed = createFileLanguageExpression(expression);
    }

    public Expression getMoveFailed() {
        return moveFailed;
    }

    public void setMoveFailed(Expression moveFailed) {
        this.moveFailed = moveFailed;
    }

    /**
     * Sets the move expression based on
     * {@link org.apache.camel.language.simple.SimpleLanguage}
     */
    public void setMove(String fileLanguageExpression) {
        String expression = configureMoveOrPreMoveExpression(fileLanguageExpression);
        this.move = createFileLanguageExpression(expression);
    }

    public Expression getPreMove() {
        return preMove;
    }

    public void setPreMove(Expression preMove) {
        this.preMove = preMove;
    }

    /**
     * Sets the pre move expression based on
     * {@link org.apache.camel.language.simple.SimpleLanguage}
     */
    public void setPreMove(String fileLanguageExpression) {
        String expression = configureMoveOrPreMoveExpression(fileLanguageExpression);
        this.preMove = createFileLanguageExpression(expression);
    }

    public Expression getMoveExisting() {
        return moveExisting;
    }

    public void setMoveExisting(Expression moveExisting) {
        this.moveExisting = moveExisting;
    }

    /**
     * Sets the move existing expression based on
     * {@link org.apache.camel.language.simple.SimpleLanguage}
     */
    public void setMoveExisting(String fileLanguageExpression) {
        String expression = configureMoveOrPreMoveExpression(fileLanguageExpression);
        this.moveExisting = createFileLanguageExpression(expression);
    }

    public Expression getFileName() {
        return fileName;
    }

    public void setFileName(Expression fileName) {
        this.fileName = fileName;
    }

    /**
     * Sets the file expression based on
     * {@link org.apache.camel.language.simple.SimpleLanguage}
     */
    public void setFileName(String fileLanguageExpression) {
        this.fileName = createFileLanguageExpression(fileLanguageExpression);
    }

    public String getDoneFileName() {
        return doneFileName;
    }

    /**
     * Sets the done file name.
     * <p/>
     * Only ${file.name} and ${file.name.noext} is supported as dynamic placeholders.
     */
    public void setDoneFileName(String doneFileName) {
        this.doneFileName = doneFileName;
    }

    public Boolean isIdempotent() {
        return idempotent != null ? idempotent : false;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        IOHelper.validateCharset(charset);
        this.charset = charset;
    }

    protected boolean isIdempotentSet() {
        return idempotent != null;
    }

    public void setIdempotent(Boolean idempotent) {
        this.idempotent = idempotent;
    }

    public Expression getIdempotentKey() {
        return idempotentKey;
    }

    public void setIdempotentKey(Expression idempotentKey) {
        this.idempotentKey = idempotentKey;
    }

    public void setIdempotentKey(String expression) {
        this.idempotentKey = createFileLanguageExpression(expression);
    }

    public IdempotentRepository<String> getIdempotentRepository() {
        return idempotentRepository;
    }

    public void setIdempotentRepository(IdempotentRepository<String> idempotentRepository) {
        this.idempotentRepository = idempotentRepository;
    }

    public GenericFileFilter<T> getFilter() {
        return filter;
    }

    public void setFilter(GenericFileFilter<T> filter) {
        this.filter = filter;
    }

    public Comparator<GenericFile<T>> getSorter() {
        return sorter;
    }

    public void setSorter(Comparator<GenericFile<T>> sorter) {
        this.sorter = sorter;
    }

    public Comparator<Exchange> getSortBy() {
        return sortBy;
    }

    public void setSortBy(Comparator<Exchange> sortBy) {
        this.sortBy = sortBy;
    }

    public void setSortBy(String expression) {
        setSortBy(expression, false);
    }

    public void setSortBy(String expression, boolean reverse) {
        setSortBy(GenericFileDefaultSorter.sortByFileLanguage(getCamelContext(), expression, reverse));
    }

    public String getTempPrefix() {
        return tempPrefix;
    }

    /**
     * Enables and uses temporary prefix when writing files, after write it will
     * be renamed to the correct name.
     */
    public void setTempPrefix(String tempPrefix) {
        this.tempPrefix = tempPrefix;
        // use only name as we set a prefix in from on the name
        setTempFileName(tempPrefix + "${file:onlyname}");
    }

    public Expression getTempFileName() {
        return tempFileName;
    }

    public void setTempFileName(Expression tempFileName) {
        this.tempFileName = tempFileName;
    }

    public void setTempFileName(String tempFileNameExpression) {
        this.tempFileName = createFileLanguageExpression(tempFileNameExpression);
    }

    public boolean isEagerDeleteTargetFile() {
        return eagerDeleteTargetFile;
    }

    public void setEagerDeleteTargetFile(boolean eagerDeleteTargetFile) {
        this.eagerDeleteTargetFile = eagerDeleteTargetFile;
    }

    public GenericFileConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new GenericFileConfiguration();
        }
        return configuration;
    }

    public void setConfiguration(GenericFileConfiguration configuration) {
        this.configuration = configuration;
    }

    public GenericFileExclusiveReadLockStrategy<T> getExclusiveReadLockStrategy() {
        return exclusiveReadLockStrategy;
    }

    public void setExclusiveReadLockStrategy(GenericFileExclusiveReadLockStrategy<T> exclusiveReadLockStrategy) {
        this.exclusiveReadLockStrategy = exclusiveReadLockStrategy;
    }

    public String getReadLock() {
        return readLock;
    }

    public void setReadLock(String readLock) {
        this.readLock = readLock;
    }

    public long getReadLockCheckInterval() {
        return readLockCheckInterval;
    }

    public void setReadLockCheckInterval(long readLockCheckInterval) {
        this.readLockCheckInterval = readLockCheckInterval;
    }

    public long getReadLockTimeout() {
        return readLockTimeout;
    }

    public void setReadLockTimeout(long readLockTimeout) {
        this.readLockTimeout = readLockTimeout;
    }

    public long getReadLockMinLength() {
        return readLockMinLength;
    }

    public void setReadLockMinLength(long readLockMinLength) {
        this.readLockMinLength = readLockMinLength;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("BufferSize must be a positive value, was " + bufferSize);
        }
        this.bufferSize = bufferSize;
    }

    public GenericFileExist getFileExist() {
        return fileExist;
    }

    public void setFileExist(GenericFileExist fileExist) {
        this.fileExist = fileExist;
    }

    public boolean isAutoCreate() {
        return autoCreate;
    }

    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    public boolean isStartingDirectoryMustExist() {
        return startingDirectoryMustExist;
    }

    public void setStartingDirectoryMustExist(boolean startingDirectoryMustExist) {
        this.startingDirectoryMustExist = startingDirectoryMustExist;
    }

    public boolean isDirectoryMustExist() {
        return directoryMustExist;
    }

    public void setDirectoryMustExist(boolean directoryMustExist) {
        this.directoryMustExist = directoryMustExist;
    }

    public GenericFileProcessStrategy<T> getProcessStrategy() {
        return processStrategy;
    }

    public void setProcessStrategy(GenericFileProcessStrategy<T> processStrategy) {
        this.processStrategy = processStrategy;
    }

    public String getLocalWorkDirectory() {
        return localWorkDirectory;
    }

    public void setLocalWorkDirectory(String localWorkDirectory) {
        this.localWorkDirectory = localWorkDirectory;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public boolean isEagerMaxMessagesPerPoll() {
        return eagerMaxMessagesPerPoll;
    }

    public void setEagerMaxMessagesPerPoll(boolean eagerMaxMessagesPerPoll) {
        this.eagerMaxMessagesPerPoll = eagerMaxMessagesPerPoll;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int getMinDepth() {
        return minDepth;
    }

    public void setMinDepth(int minDepth) {
        this.minDepth = minDepth;
    }

    public IdempotentRepository<String> getInProgressRepository() {
        return inProgressRepository;
    }

    public void setInProgressRepository(IdempotentRepository<String> inProgressRepository) {
        this.inProgressRepository = inProgressRepository;
    }

    public boolean isKeepLastModified() {
        return keepLastModified;
    }

    public void setKeepLastModified(boolean keepLastModified) {
        this.keepLastModified = keepLastModified;
    }

    public boolean isAllowNullBody() {
        return allowNullBody;
    }
    
    public void setAllowNullBody(boolean allowNullBody) {
        this.allowNullBody = allowNullBody;
    }
    
    /**
     * Configures the given message with the file which sets the body to the
     * file object.
     */
    public void configureMessage(GenericFile<T> file, Message message) {
        message.setBody(file);

        if (flatten) {
            // when flatten the file name should not contain any paths
            message.setHeader(Exchange.FILE_NAME, file.getFileNameOnly());
        } else {
            // compute name to set on header that should be relative to starting directory
            String name = file.isAbsolute() ? file.getAbsoluteFilePath() : file.getRelativeFilePath();

            // skip leading endpoint configured directory
            String endpointPath = getConfiguration().getDirectory() + getFileSeparator();
            if (ObjectHelper.isNotEmpty(endpointPath) && name.startsWith(endpointPath)) {
                name = ObjectHelper.after(name, endpointPath);
            }

            // adjust filename
            message.setHeader(Exchange.FILE_NAME, name);
        }
    }

    /**
     * Set up the exchange properties with the options of the file endpoint
     */
    public void configureExchange(Exchange exchange) {
        // Now we just set the charset property here
        if (getCharset() != null) {
            exchange.setProperty(Exchange.CHARSET_NAME, getCharset());
        }
    }

    /**
     * Strategy to configure the move, preMove, or moveExisting option based on a String input.
     *
     * @param expression the original string input
     * @return configured string or the original if no modifications is needed
     */
    protected String configureMoveOrPreMoveExpression(String expression) {
        // if the expression already have ${ } placeholders then pass it unmodified
        if (StringHelper.hasStartToken(expression, "simple")) {
            return expression;
        }

        // remove trailing slash
        expression = FileUtil.stripTrailingSeparator(expression);

        StringBuilder sb = new StringBuilder();

        // if relative then insert start with the parent folder
        if (!isAbsolute(expression)) {
            sb.append("${file:parent}");
            sb.append(getFileSeparator());
        }
        // insert the directory the end user provided
        sb.append(expression);
        // append only the filename (file:name can contain a relative path, so we must use onlyname)
        sb.append(getFileSeparator());
        sb.append("${file:onlyname}");

        return sb.toString();
    }

    protected Map<String, Object> getParamsAsMap() {
        Map<String, Object> params = new HashMap<String, Object>();

        if (isNoop()) {
            params.put("noop", Boolean.toString(true));
        }
        if (isDelete()) {
            params.put("delete", Boolean.toString(true));
        }
        if (move != null) {
            params.put("move", move);
        }
        if (moveFailed != null) {
            params.put("moveFailed", moveFailed);
        }
        if (preMove != null) {
            params.put("preMove", preMove);
        }
        if (exclusiveReadLockStrategy != null) {
            params.put("exclusiveReadLockStrategy", exclusiveReadLockStrategy);
        }
        if (readLock != null) {
            params.put("readLock", readLock);
        }
        if (readLockCheckInterval > 0) {
            params.put("readLockCheckInterval", readLockCheckInterval);
        }
        if (readLockTimeout > 0) {
            params.put("readLockTimeout", readLockTimeout);
        }
        params.put("readLockMinLength", readLockMinLength);

        return params;
    }

    private Expression createFileLanguageExpression(String expression) {
        Language language;
        // only use file language if the name is complex (eg. using $)
        if (expression.contains("$")) {
            language = getCamelContext().resolveLanguage("file");
        } else {
            language = getCamelContext().resolveLanguage("constant");
        }
        return language.createExpression(expression);
    }

    /**
     * Creates the associated name of the done file based on the given file name.
     * <p/>
     * This method should only be invoked if a done filename property has been set on this endpoint.
     *
     * @param fileName the file name
     * @return name of the associated done file name
     */
    protected String createDoneFileName(String fileName) {
        String pattern = getDoneFileName();
        ObjectHelper.notEmpty(pattern, "doneFileName", pattern);

        // we only support ${file:name} or ${file:name.noext} as dynamic placeholders for done files
        String path = FileUtil.onlyPath(fileName);
        String onlyName = FileUtil.stripPath(fileName);

        pattern = pattern.replaceFirst("\\$\\{file:name\\}", onlyName);
        pattern = pattern.replaceFirst("\\$simple\\{file:name\\}", onlyName);
        pattern = pattern.replaceFirst("\\$\\{file:name.noext\\}", FileUtil.stripExt(onlyName));
        pattern = pattern.replaceFirst("\\$simple\\{file:name.noext\\}", FileUtil.stripExt(onlyName));

        // must be able to resolve all placeholders supported
        if (StringHelper.hasStartToken(pattern, "simple")) {
            throw new ExpressionIllegalSyntaxException(fileName + ". Cannot resolve reminder: " + pattern);
        }

        String answer = pattern;
        if (ObjectHelper.isNotEmpty(path) && ObjectHelper.isNotEmpty(pattern)) {
            // done file must always be in same directory as the real file name
            answer = path + getFileSeparator() + pattern;
        }

        if (getConfiguration().needToNormalize()) {
            // must normalize path to cater for Windows and other OS
            answer = FileUtil.normalizePath(answer);
        }

        return answer;
    }

    /**
     * Is the given file a done file?
     * <p/>
     * This method should only be invoked if a done filename property has been set on this endpoint.
     *
     * @param fileName the file name
     * @return <tt>true</tt> if its a done file, <tt>false</tt> otherwise
     */
    protected boolean isDoneFile(String fileName) {
        String pattern = getDoneFileName();
        ObjectHelper.notEmpty(pattern, "doneFileName", pattern);

        if (!StringHelper.hasStartToken(pattern, "simple")) {
            // no tokens, so just match names directly
            return pattern.equals(fileName);
        }

        // the static part of the pattern, is that a prefix or suffix?
        // its a prefix if ${ start token is not at the start of the pattern
        boolean prefix = pattern.indexOf("${") > 0;

        // remove dynamic parts of the pattern so we only got the static part left
        pattern = pattern.replaceFirst("\\$\\{file:name\\}", "");
        pattern = pattern.replaceFirst("\\$simple\\{file:name\\}", "");
        pattern = pattern.replaceFirst("\\$\\{file:name.noext\\}", "");
        pattern = pattern.replaceFirst("\\$simple\\{file:name.noext\\}", "");

        // must be able to resolve all placeholders supported
        if (StringHelper.hasStartToken(pattern, "simple")) {
            throw new ExpressionIllegalSyntaxException(fileName + ". Cannot resolve reminder: " + pattern);
        }

        if (prefix) {
            return fileName.startsWith(pattern);
        } else {
            return fileName.endsWith(pattern);
        }
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startServices(inProgressRepository, idempotentRepository);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopServices(inProgressRepository, idempotentRepository);
    }
}
