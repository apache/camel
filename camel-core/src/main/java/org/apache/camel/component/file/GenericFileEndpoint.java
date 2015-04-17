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
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.UriParam;
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

    protected static final String DEFAULT_STRATEGYFACTORY_CLASS = "org.apache.camel.component.file.strategy.GenericFileProcessStrategyFactory";
    protected static final int DEFAULT_IDEMPOTENT_CACHE_SIZE = 1000;
    
    private static final Integer CHMOD_WRITE_MASK = 02;
    private static final Integer CHMOD_READ_MASK = 04;
    private static final Integer CHMOD_EXECUTE_MASK = 01;

    protected final Logger log = LoggerFactory.getLogger(getClass());

    // common options

    @UriParam(defaultValue = "true")
    protected boolean autoCreate = true;
    @UriParam(defaultValue = "" + FileUtil.BUFFER_SIZE)
    protected int bufferSize = FileUtil.BUFFER_SIZE;
    @UriParam
    protected boolean flatten;
    @UriParam
    protected String charset;
    @UriParam
    protected Expression fileName;

    // producer options

    @UriParam(label = "producer", defaultValue = "Override")
    protected GenericFileExist fileExist = GenericFileExist.Override;
    @UriParam(label = "producer")
    protected String tempPrefix;
    @UriParam(label = "producer")
    protected Expression tempFileName;
    @UriParam(label = "producer", defaultValue = "true")
    protected boolean eagerDeleteTargetFile = true;
    @UriParam(label = "producer")
    protected boolean keepLastModified;
    @UriParam(label = "producer")
    protected String doneFileName;
    @UriParam(label = "producer")
    protected boolean allowNullBody;
    @UriParam(label = "producer")
    protected String chmod;

    // consumer options

    @UriParam
    protected GenericFileConfiguration configuration;
    @UriParam(label = "consumer")
    protected GenericFileProcessStrategy<T> processStrategy;
    @UriParam(label = "consumer")
    protected IdempotentRepository<String> inProgressRepository = new MemoryIdempotentRepository();
    @UriParam(label = "consumer")
    protected String localWorkDirectory;
    @UriParam(label = "consumer")
    protected boolean startingDirectoryMustExist;
    @UriParam(label = "consumer")
    protected boolean directoryMustExist;
    @UriParam(label = "consumer")
    protected boolean noop;
    @UriParam(label = "consumer")
    protected boolean recursive;
    @UriParam(label = "consumer")
    protected boolean delete;
    @UriParam(label = "consumer")
    protected int maxMessagesPerPoll;
    @UriParam(label = "consumer", defaultValue = "true")
    protected boolean eagerMaxMessagesPerPoll = true;
    @UriParam(label = "consumer", defaultValue = "" + Integer.MAX_VALUE)
    protected int maxDepth = Integer.MAX_VALUE;
    @UriParam(label = "consumer")
    protected int minDepth;
    @UriParam(label = "consumer")
    protected String include;
    @UriParam(label = "consumer")
    protected String exclude;
    @UriParam(label = "consumer")
    protected Expression move;
    @UriParam(label = "consumer")
    protected Expression moveFailed;
    @UriParam(label = "consumer")
    protected Expression preMove;
    @UriParam(label = "producer")
    protected Expression moveExisting;
    @UriParam(label = "consumer")
    protected Boolean idempotent;
    @UriParam(label = "consumer")
    protected Expression idempotentKey;
    @UriParam(label = "consumer")
    protected IdempotentRepository<String> idempotentRepository;
    @UriParam(label = "consumer")
    protected GenericFileFilter<T> filter;
    protected volatile AntPathMatcherGenericFileFilter<T> antFilter;
    @UriParam(label = "consumer")
    protected String antInclude;
    @UriParam(label = "consumer")
    protected String antExclude;
    @UriParam(label = "consumer")
    protected Comparator<GenericFile<T>> sorter;
    @UriParam(label = "consumer")
    protected Comparator<Exchange> sortBy;
    @UriParam(label = "consumer", enums = "none,markerFile,fileLock,rename,changed")
    protected String readLock = "none";
    @UriParam(label = "consumer", defaultValue = "1000")
    protected long readLockCheckInterval = 1000;
    @UriParam(label = "consumer", defaultValue = "10000")
    protected long readLockTimeout = 10000;
    @UriParam(label = "consumer", defaultValue = "true")
    protected boolean readLockMarkerFile = true;
    @UriParam(label = "consumer", defaultValue = "WARN")
    protected LoggingLevel readLockLoggingLevel = LoggingLevel.WARN;
    @UriParam(label = "consumer", defaultValue = "1")
    protected long readLockMinLength = 1;
    @UriParam(label = "consumer", defaultValue = "0")
    protected long readLockMinAge;
    @UriParam(label = "consumer")
    protected GenericFileExclusiveReadLockStrategy<T> exclusiveReadLockStrategy;

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

    /**
     * Chmod value must be between 000 and 777; If there is a leading digit like in 0755 we will ignore it.
     */
    public boolean chmodPermissionsAreValid(String chmod) {
        if (chmod == null || chmod.length() < 3 || chmod.length() > 4) {
            return false;
        }
        String permissionsString = chmod.trim().substring(chmod.length() - 3);  // if 4 digits chop off leading one
        for (int i = 0; i < permissionsString.length(); i++) {
            Character c = permissionsString.charAt(i);
            if (!Character.isDigit(c) || Integer.parseInt(c.toString()) > 7) {
                return false;
            }
        }
        return true;
    }

    public Set<PosixFilePermission> getPermissions() {
        Set<PosixFilePermission> permissions = new HashSet<PosixFilePermission>();
        if (ObjectHelper.isEmpty(chmod)) {
            return permissions;
        }

        String chmodString = chmod.substring(chmod.length() - 3);  // if 4 digits chop off leading one

        Integer ownerValue = Integer.parseInt(chmodString.substring(0, 1));
        Integer groupValue = Integer.parseInt(chmodString.substring(1, 2));
        Integer othersValue = Integer.parseInt(chmodString.substring(2, 3));

        if ((ownerValue & CHMOD_WRITE_MASK) > 0) {
            permissions.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((ownerValue & CHMOD_READ_MASK) > 0) {
            permissions.add(PosixFilePermission.OWNER_READ);
        }
        if ((ownerValue & CHMOD_EXECUTE_MASK) > 0) {
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
        }

        if ((groupValue & CHMOD_WRITE_MASK) > 0) {
            permissions.add(PosixFilePermission.GROUP_WRITE);
        }
        if ((groupValue & CHMOD_READ_MASK) > 0) {
            permissions.add(PosixFilePermission.GROUP_READ);
        }
        if ((groupValue & CHMOD_EXECUTE_MASK) > 0) {
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
        }

        if ((othersValue & CHMOD_WRITE_MASK) > 0) {
            permissions.add(PosixFilePermission.OTHERS_WRITE);
        }
        if ((othersValue & CHMOD_READ_MASK) > 0) {
            permissions.add(PosixFilePermission.OTHERS_READ);
        }
        if ((othersValue & CHMOD_EXECUTE_MASK) > 0) {
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        }

        return permissions;
    }

    public String getChmod() {
        return chmod;
    }

    /**
     * Specify the file permissions which is sent by the producer, the chmod value must be between 000 and 777;
     * If there is a leading digit like in 0755 we will ignore it.
     */
    public void setChmod(String chmod) throws Exception {
        if (ObjectHelper.isNotEmpty(chmod) && chmodPermissionsAreValid(chmod)) {
            this.chmod = chmod.trim();
        } else {
            throw new IllegalArgumentException("chmod option [" + chmod + "] is not valid");
        }
    }

    public boolean isNoop() {
        return noop;
    }

    /**
     * If true, the file is not moved or deleted in any way.
     * This option is good for readonly data, or for ETL type requirements.
     * If noop=true, Camel will set idempotent=true as well, to avoid consuming the same files over and over again.
     */
    public void setNoop(boolean noop) {
        this.noop = noop;
    }

    public boolean isRecursive() {
        return recursive;
    }

    /**
     * If a directory, will look for files in all the sub-directories as well.
     */
    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public String getInclude() {
        return include;
    }

    /**
     * Is used to include files, if filename matches the regex pattern.
     */
    public void setInclude(String include) {
        this.include = include;
    }

    public String getExclude() {
        return exclude;
    }

    /**
     * Is used to exclude files, if filename matches the regex pattern.
     */
    public void setExclude(String exclude) {
        this.exclude = exclude;
    }

    public String getAntInclude() {
        return antInclude;
    }

    /**
     * Ant style filter inclusion.
     * Multiple inclusions may be specified in comma-delimited format.
     */
    public void setAntInclude(String antInclude) {
        this.antInclude = antInclude;
        if (this.antFilter == null) {
            this.antFilter = new AntPathMatcherGenericFileFilter<T>();
        }
        this.antFilter.setIncludes(antInclude);
    }

    public String getAntExclude() {
        return antExclude;
    }

    /**
     * Ant style filter exclusion. If both antInclude and antExclude are used, antExclude takes precedence over antInclude.
     * Multiple exclusions may be specified in comma-delimited format.
     */
    public void setAntExclude(String antExclude) {
        this.antExclude = antExclude;
        if (this.antFilter == null) {
            this.antFilter = new AntPathMatcherGenericFileFilter<T>();
        }
        this.antFilter.setExcludes(antExclude);
    }

    /**
     * Sets case sensitive flag on {@link org.apache.camel.component.file.AntPathMatcherFileFilter}
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

    /**
     * If true, the file will be deleted after it is processed successfully.
     */
    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public boolean isFlatten() {
        return flatten;
    }

    /**
     * Flatten is used to flatten the file name path to strip any leading paths, so it's just the file name.
     * This allows you to consume recursively into sub-directories, but when you eg write the files to another directory
     * they will be written in a single directory.
     * Setting this to true on the producer enforces that any file name in CamelFileName header
     * will be stripped for any leading paths.
     */
    public void setFlatten(boolean flatten) {
        this.flatten = flatten;
    }

    public Expression getMove() {
        return move;
    }

    /**
     * Expression (such as Simple Language) used to dynamically set the filename when moving it after processing.
     * To move files into a .done subdirectory just enter .done.
     */
    public void setMove(Expression move) {
        this.move = move;
    }

    /**
     * @see #setMove(org.apache.camel.Expression)
     */
    public void setMove(String fileLanguageExpression) {
        String expression = configureMoveOrPreMoveExpression(fileLanguageExpression);
        this.move = createFileLanguageExpression(expression);
    }

    public Expression getMoveFailed() {
        return moveFailed;
    }

    /**
     * Sets the move failure expression based on Simple language.
     * For example, to move files into a .error subdirectory use: .error.
     * Note: When moving the files to the fail location Camel will handle the error and will not pick up the file again.
     */
    public void setMoveFailed(Expression moveFailed) {
        this.moveFailed = moveFailed;
    }

    public void setMoveFailed(String fileLanguageExpression) {
        String expression = configureMoveOrPreMoveExpression(fileLanguageExpression);
        this.moveFailed = createFileLanguageExpression(expression);
    }

    public Expression getPreMove() {
        return preMove;
    }

    /**
     * Expression (such as File Language) used to dynamically set the filename when moving it before processing.
     * For example to move in-progress files into the order directory set this value to order.
     */
    public void setPreMove(Expression preMove) {
        this.preMove = preMove;
    }

    public void setPreMove(String fileLanguageExpression) {
        String expression = configureMoveOrPreMoveExpression(fileLanguageExpression);
        this.preMove = createFileLanguageExpression(expression);
    }

    public Expression getMoveExisting() {
        return moveExisting;
    }

    /**
     * Expression (such as File Language) used to compute file name to use when fileExist=Move is configured.
     * To move files into a backup subdirectory just enter backup.
     * This option only supports the following File Language tokens: "file:name", "file:name.ext", "file:name.noext", "file:onlyname",
     * "file:onlyname.noext", "file:ext", and "file:parent". Notice the "file:parent" is not supported by the FTP component,
     * as the FTP component can only move any existing files to a relative directory based on current dir as base.
     */
    public void setMoveExisting(Expression moveExisting) {
        this.moveExisting = moveExisting;
    }

    public void setMoveExisting(String fileLanguageExpression) {
        String expression = configureMoveOrPreMoveExpression(fileLanguageExpression);
        this.moveExisting = createFileLanguageExpression(expression);
    }

    public Expression getFileName() {
        return fileName;
    }

    /**
     * Use Expression such as File Language to dynamically set the filename.
     * For consumers, it's used as a filename filter.
     * For producers, it's used to evaluate the filename to write.
     * If an expression is set, it take precedence over the CamelFileName header. (Note: The header itself can also be an Expression).
     * The expression options support both String and Expression types.
     * If the expression is a String type, it is always evaluated using the File Language.
     * If the expression is an Expression type, the specified Expression type is used - this allows you,
     * for instance, to use OGNL expressions. For the consumer, you can use it to filter filenames,
     * so you can for instance consume today's file using the File Language syntax: mydata-${date:now:yyyyMMdd}.txt.
     * The producers support the CamelOverruleFileName header which takes precedence over any existing CamelFileName header;
     * the CamelOverruleFileName is a header that is used only once, and makes it easier as this avoids to temporary
     * store CamelFileName and have to restore it afterwards.
     */
    public void setFileName(Expression fileName) {
        this.fileName = fileName;
    }

    public void setFileName(String fileLanguageExpression) {
        this.fileName = createFileLanguageExpression(fileLanguageExpression);
    }

    public String getDoneFileName() {
        return doneFileName;
    }

    /**
     * If provided, then Camel will write a 2nd done file when the original file has been written.
     * The done file will be empty. This option configures what file name to use.
     * Either you can specify a fixed name. Or you can use dynamic placeholders.
     * The done file will always be written in the same folder as the original file.
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

    /**
     * This option is used to specify the encoding of the file.
     * You can use this on the consumer, to specify the encodings of the files, which allow Camel to know the charset
     * it should load the file content in case the file content is being accessed.
     * Likewise when writing a file, you can use this option to specify which charset to write the file as well.
     */
    public void setCharset(String charset) {
        IOHelper.validateCharset(charset);
        this.charset = charset;
    }

    protected boolean isIdempotentSet() {
        return idempotent != null;
    }

    /**
     * Option to use the Idempotent Consumer EIP pattern to let Camel skip already processed files.
     * Will by default use a memory based LRUCache that holds 1000 entries. If noop=true then idempotent will be enabled
     * as well to avoid consuming the same files over and over again.
     */
    public void setIdempotent(Boolean idempotent) {
        this.idempotent = idempotent;
    }

    public Expression getIdempotentKey() {
        return idempotentKey;
    }

    /**
     * To use a custom idempotent key. By default the absolute path of the file is used.
     * You can use the File Language, for example to use the file name and file size, you can do:
     * <tt>idempotentKey=${file:name}-${file:size}</tt>
     */
    public void setIdempotentKey(Expression idempotentKey) {
        this.idempotentKey = idempotentKey;
    }

    public void setIdempotentKey(String expression) {
        this.idempotentKey = createFileLanguageExpression(expression);
    }

    public IdempotentRepository<String> getIdempotentRepository() {
        return idempotentRepository;
    }

    /**
     * A pluggable repository org.apache.camel.spi.IdempotentRepository which by default use MemoryMessageIdRepository
     * if none is specified and idempotent is true.
     */
    public void setIdempotentRepository(IdempotentRepository<String> idempotentRepository) {
        this.idempotentRepository = idempotentRepository;
    }

    public GenericFileFilter<T> getFilter() {
        return filter;
    }

    /**
     * Pluggable filter as a org.apache.camel.component.file.GenericFileFilter class.
     * Will skip files if filter returns false in its accept() method.
     */
    public void setFilter(GenericFileFilter<T> filter) {
        this.filter = filter;
    }

    public Comparator<GenericFile<T>> getSorter() {
        return sorter;
    }

    /**
     * Pluggable sorter as a java.util.Comparator<org.apache.camel.component.file.GenericFile> class.
     */
    public void setSorter(Comparator<GenericFile<T>> sorter) {
        this.sorter = sorter;
    }

    public Comparator<Exchange> getSortBy() {
        return sortBy;
    }

    /**
     * Built-in sort by using the File Language.
     * Supports nested sorts, so you can have a sort by file name and as a 2nd group sort by modified date.
     */
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
     * This option is used to write the file using a temporary name and then, after the write is complete,
     * rename it to the real name. Can be used to identify files being written and also avoid consumers
     * (not using exclusive read locks) reading in progress files. Is often used by FTP when uploading big files.
     */
    public void setTempPrefix(String tempPrefix) {
        this.tempPrefix = tempPrefix;
        // use only name as we set a prefix in from on the name
        setTempFileName(tempPrefix + "${file:onlyname}");
    }

    public Expression getTempFileName() {
        return tempFileName;
    }

    /**
     * The same as tempPrefix option but offering a more fine grained control on the naming of the temporary filename as it uses the File Language.
     */
    public void setTempFileName(Expression tempFileName) {
        this.tempFileName = tempFileName;
    }

    public void setTempFileName(String tempFileNameExpression) {
        this.tempFileName = createFileLanguageExpression(tempFileNameExpression);
    }

    public boolean isEagerDeleteTargetFile() {
        return eagerDeleteTargetFile;
    }

    /**
     * Whether or not to eagerly delete any existing target file.
     * This option only applies when you use fileExists=Override and the tempFileName option as well.
     * You can use this to disable (set it to false) deleting the target file before the temp file is written.
     * For example you may write big files and want the target file to exists during the temp file is being written.
     * This ensure the target file is only deleted until the very last moment, just before the temp file is being
     * renamed to the target filename. This option is also used to control whether to delete any existing files when
     * fileExist=Move is enabled, and an existing file exists.
     * If this option copyAndDeleteOnRenameFails false, then an exception will be thrown if an existing file existed,
     * if its true, then the existing file is deleted before the move operation.
     */
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

    /**
     * Pluggable read-lock as a org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy implementation.
     */
    public void setExclusiveReadLockStrategy(GenericFileExclusiveReadLockStrategy<T> exclusiveReadLockStrategy) {
        this.exclusiveReadLockStrategy = exclusiveReadLockStrategy;
    }

    public String getReadLock() {
        return readLock;
    }

    /**
     * Used by consumer, to only poll the files if it has exclusive read-lock on the file (i.e. the file is not in-progress or being written).
     * Camel will wait until the file lock is granted.
     * <p/>
     * This option provides the build in strategies:
     * <ul>
     *     <li>none - No read lock is in use
     *     <li>markerFile - Camel creates a marker file (fileName.camelLock) and then holds a lock on it. This option is not available for the FTP component
     *     <li>changed - Changed is using file length/modification timestamp to detect whether the file is currently being copied or not. Will at least use 1 sec
     *     to determine this, so this option cannot consume files as fast as the others, but can be more reliable as the JDK IO API cannot
     *     always determine whether a file is currently being used by another process. The option readLockCheckInterval can be used to set the check frequency.</li>
     *     <li>fileLock - is for using java.nio.channels.FileLock. This option is not avail for the FTP component. This approach should be avoided when accessing
     *     a remote file system via a mount/share unless that file system supports distributed file locks.</li>
     *     <li>rename - rename is for using a try to rename the file as a test if we can get exclusive read-lock.</li>
     * </ul>
     */
    public void setReadLock(String readLock) {
        this.readLock = readLock;
    }

    public long getReadLockCheckInterval() {
        return readLockCheckInterval;
    }

    /**
     * Interval in millis for the read-lock, if supported by the read lock.
     * This interval is used for sleeping between attempts to acquire the read lock.
     * For example when using the changed read lock, you can set a higher interval period to cater for slow writes.
     * The default of 1 sec. may be too fast if the producer is very slow writing the file.
     * <p/>
     * Notice: For FTP the default readLockCheckInterval is 5000.
     * <p/>
     * The readLockTimeout value must be higher than readLockCheckInterval, but a rule of thumb is to have a timeout
     * that is at least 2 or more times higher than the readLockCheckInterval. This is needed to ensure that amble
     * time is allowed for the read lock process to try to grab the lock before the timeout was hit.
     */
    public void setReadLockCheckInterval(long readLockCheckInterval) {
        this.readLockCheckInterval = readLockCheckInterval;
    }

    public long getReadLockTimeout() {
        return readLockTimeout;
    }

    /**
     * Optional timeout in millis for the read-lock, if supported by the read-lock.
     * If the read-lock could not be granted and the timeout triggered, then Camel will skip the file.
     * At next poll Camel, will try the file again, and this time maybe the read-lock could be granted.
     * Use a value of 0 or lower to indicate forever. Currently fileLock, changed and rename support the timeout.
     * <p/>
     * Notice: For FTP the default readLockTimeout value is 20000 instead of 10000.
     * <p/>
     * The readLockTimeout value must be higher than readLockCheckInterval, but a rule of thumb is to have a timeout
     * that is at least 2 or more times higher than the readLockCheckInterval. This is needed to ensure that amble
     * time is allowed for the read lock process to try to grab the lock before the timeout was hit.
     */
    public void setReadLockTimeout(long readLockTimeout) {
        this.readLockTimeout = readLockTimeout;
    }

    public boolean isReadLockMarkerFile() {
        return readLockMarkerFile;
    }

    /**
     * Whether to use marker file with the changed, rename, or exclusive read lock types.
     * By default a marker file is used as well to guard against other processes picking up the same files.
     * This behavior can be turned off by setting this option to false.
     * For example if you do not want to write marker files to the file systems by the Camel application.
     */
    public void setReadLockMarkerFile(boolean readLockMarkerFile) {
        this.readLockMarkerFile = readLockMarkerFile;
    }

    public LoggingLevel getReadLockLoggingLevel() {
        return readLockLoggingLevel;
    }

    /**
     * Logging level used when a read lock could not be acquired.
     * By default a WARN is logged. You can change this level, for example to OFF to not have any logging.
     * This option is only applicable for readLock of types: changed, fileLock, rename.
     */
    public void setReadLockLoggingLevel(LoggingLevel readLockLoggingLevel) {
        this.readLockLoggingLevel = readLockLoggingLevel;
    }

    public long getReadLockMinLength() {
        return readLockMinLength;
    }

    /**
     * This option applied only for readLock=changed. This option allows you to configure a minimum file length.
     * By default Camel expects the file to contain data, and thus the default value is 1.
     * You can set this option to zero, to allow consuming zero-length files.
     */
    public void setReadLockMinLength(long readLockMinLength) {
        this.readLockMinLength = readLockMinLength;
    }

    public long getReadLockMinAge() {
        return readLockMinAge;
    }

    /**
     * This option applied only for readLock=change.
     * This options allows to specify a minimum age the file must be before attempting to acquire the read lock.
     * For example use readLockMinAge=300s to require the file is at last 5 minutes old.
     * This can speedup the changed read lock as it will only attempt to acquire files which are at least that given age.
     */
    public void setReadLockMinAge(long readLockMinAge) {
        this.readLockMinAge = readLockMinAge;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Write buffer sized in bytes.
     */
    public void setBufferSize(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("BufferSize must be a positive value, was " + bufferSize);
        }
        this.bufferSize = bufferSize;
    }

    public GenericFileExist getFileExist() {
        return fileExist;
    }

    /**
     * What to do if a file already exists with the same name.
     *
     * Override, which is the default, replaces the existing file.
     * <ul>
     *   <li>Append - adds content to the existing file.</li>
     *   <li>Fail - throws a GenericFileOperationException, indicating that there is already an existing file.</li>
     *   <li>Ignore - silently ignores the problem and does not override the existing file, but assumes everything is okay.</li>
     *   <li>Move - option requires to use the moveExisting option to be configured as well.
     *   The option eagerDeleteTargetFile can be used to control what to do if an moving the file, and there exists already an existing file,
     *   otherwise causing the move operation to fail.
     *   The Move option will move any existing files, before writing the target file.</li>
     *   <li>TryRename Camel is only applicable if tempFileName option is in use. This allows to try renaming the file from the temporary name to the actual name,
     *   without doing any exists check.This check may be faster on some file systems and especially FTP servers.</li>
     * </ul>
     */
    public void setFileExist(GenericFileExist fileExist) {
        this.fileExist = fileExist;
    }

    public boolean isAutoCreate() {
        return autoCreate;
    }

    /**
     * Automatically create missing directories in the file's pathname. For the file consumer, that means creating the starting directory.
     * For the file producer, it means the directory the files should be written to.
     */
    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    public boolean isStartingDirectoryMustExist() {
        return startingDirectoryMustExist;
    }

    /**
     * Whether the starting directory must exist. Mind that the autoCreate option is default enabled,
     * which means the starting directory is normally auto created if it doesn't exist.
     * You can disable autoCreate and enable this to ensure the starting directory must exist. Will thrown an exception if the directory doesn't exist.
     */
    public void setStartingDirectoryMustExist(boolean startingDirectoryMustExist) {
        this.startingDirectoryMustExist = startingDirectoryMustExist;
    }

    public boolean isDirectoryMustExist() {
        return directoryMustExist;
    }

    /**
     * Similar to startingDirectoryMustExist but this applies during polling recursive sub directories.
     */
    public void setDirectoryMustExist(boolean directoryMustExist) {
        this.directoryMustExist = directoryMustExist;
    }

    public GenericFileProcessStrategy<T> getProcessStrategy() {
        return processStrategy;
    }

    /**
     * A pluggable org.apache.camel.component.file.GenericFileProcessStrategy allowing you to implement your own readLock option or similar.
     * Can also be used when special conditions must be met before a file can be consumed, such as a special ready file exists.
     * If this option is set then the readLock option does not apply.
     */
    public void setProcessStrategy(GenericFileProcessStrategy<T> processStrategy) {
        this.processStrategy = processStrategy;
    }

    public String getLocalWorkDirectory() {
        return localWorkDirectory;
    }

    /**
     * When consuming, a local work directory can be used to store the remote file content directly in local files,
     * to avoid loading the content into memory. This is beneficial, if you consume a very big remote file and thus can conserve memory.
     */
    public void setLocalWorkDirectory(String localWorkDirectory) {
        this.localWorkDirectory = localWorkDirectory;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * Tlo define a maximum messages to gather per poll.
     * By default no maximum is set. Can be used to set a limit of e.g. 1000 to avoid when starting up the server that there are thousands of files.
     * Set a value of 0 or negative to disabled it.
     * Notice: If this option is in use then the File and FTP components will limit before any sorting.
     * For example if you have 100000 files and use maxMessagesPerPoll=500, then only the first 500 files will be picked up, and then sorted.
     * You can use the eagerMaxMessagesPerPoll option and set this to false to allow to scan all files first and then sort afterwards.
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public boolean isEagerMaxMessagesPerPoll() {
        return eagerMaxMessagesPerPoll;
    }

    /**
     * Allows for controlling whether the limit from maxMessagesPerPoll is eager or not.
     * If eager then the limit is during the scanning of files. Where as false would scan all files, and then perform sorting.
     * Setting this option to false allows for sorting all files first, and then limit the poll. Mind that this requires a
     * higher memory usage as all file details are in memory to perform the sorting.
     */
    public void setEagerMaxMessagesPerPoll(boolean eagerMaxMessagesPerPoll) {
        this.eagerMaxMessagesPerPoll = eagerMaxMessagesPerPoll;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * The maximum depth to traverse when recursively processing a directory.
     */
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int getMinDepth() {
        return minDepth;
    }

    /**
     * The minimum depth to start processing when recursively processing a directory.
     * Using minDepth=1 means the base directory. Using minDepth=2 means the first sub directory.
     */
    public void setMinDepth(int minDepth) {
        this.minDepth = minDepth;
    }

    public IdempotentRepository<String> getInProgressRepository() {
        return inProgressRepository;
    }

    /**
     * A pluggable in-progress repository org.apache.camel.spi.IdempotentRepository.
     * The in-progress repository is used to account the current in progress files being consumed. By default a memory based repository is used.
     */
    public void setInProgressRepository(IdempotentRepository<String> inProgressRepository) {
        this.inProgressRepository = inProgressRepository;
    }

    public boolean isKeepLastModified() {
        return keepLastModified;
    }

    /**
     * Will keep the last modified timestamp from the source file (if any).
     * Will use the Exchange.FILE_LAST_MODIFIED header to located the timestamp.
     * This header can contain either a java.util.Date or long with the timestamp.
     * If the timestamp exists and the option is enabled it will set this timestamp on the written file.
     * Note: This option only applies to the file producer. You cannot use this option with any of the ftp producers.
     */
    public void setKeepLastModified(boolean keepLastModified) {
        this.keepLastModified = keepLastModified;
    }

    public boolean isAllowNullBody() {
        return allowNullBody;
    }

    /**
     * Used to specify if a null body is allowed during file writing.
     * If set to true then an empty file will be created, when set to false, and attempting to send a null body to the file component,
     * a GenericFileWriteException of 'Cannot write null body to file.' will be thrown.
     * If the `fileExist` option is set to 'Override', then the file will be truncated, and if set to `append` the file will remain unchanged.
     */
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

            // need to normalize paths to ensure we can match using startsWith
            endpointPath = FileUtil.normalizePath(endpointPath);
            String copyOfName = FileUtil.normalizePath(name);
            if (ObjectHelper.isNotEmpty(endpointPath) && copyOfName.startsWith(endpointPath)) {
                name = name.substring(endpointPath.length());
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
        params.put("readLockMarkerFile", readLockMarkerFile);
        params.put("readLockMinLength", readLockMinLength);
        params.put("readLockLoggingLevel", readLockLoggingLevel);
        params.put("readLockMinAge", readLockMinAge);

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
        // validate that the read lock options is valid for the process strategy
        if (!"none".equals(readLock) && !"off".equals(readLock)) {
            if (readLockTimeout > 0 && readLockMinAge > 0 && readLockTimeout <= readLockCheckInterval + readLockMinAge) {
                throw new IllegalArgumentException("The option readLockTimeout must be higher than readLockCheckInterval + readLockMinAge"
                    + ", was readLockTimeout=" + readLockTimeout + ", readLockCheckInterval+readLockMinAge=" + (readLockCheckInterval + readLockMinAge)
                    + ". A good practice is to let the readLockTimeout be at least readLockMinAge + 2 times the readLockCheckInterval"
                    + " to ensure that the read lock procedure has enough time to acquire the lock.");
            }
            if (readLockTimeout > 0 && readLockTimeout <= readLockCheckInterval) {
                throw new IllegalArgumentException("The option readLockTimeout must be higher than readLockCheckInterval"
                        + ", was readLockTimeout=" + readLockTimeout + ", readLockCheckInterval=" + readLockCheckInterval
                        + ". A good practice is to let the readLockTimeout be at least 3 times higher than the readLockCheckInterval"
                        + " to ensure that the read lock procedure has enough time to acquire the lock.");
            }
        }

        ServiceHelper.startServices(inProgressRepository, idempotentRepository);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopServices(inProgressRepository, idempotentRepository);
    }
}
