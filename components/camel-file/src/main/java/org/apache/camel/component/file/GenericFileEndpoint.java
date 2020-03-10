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
package org.apache.camel.component.file;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.file.strategy.FileMoveExistingStrategy;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for file endpoints
 */
public abstract class GenericFileEndpoint<T> extends ScheduledPollEndpoint implements BrowsableEndpoint {

    protected static final String DEFAULT_STRATEGYFACTORY_CLASS = "org.apache.camel.component.file.strategy.GenericFileProcessStrategyFactory";
    protected static final int DEFAULT_IDEMPOTENT_CACHE_SIZE = 1000;
    protected static final int DEFAULT_IN_PROGRESS_CACHE_SIZE = 50000;

    private static final Logger LOG = LoggerFactory.getLogger(GenericFileEndpoint.class);

    // common options

    @UriParam(label = "advanced", defaultValue = "true", description = "Automatically create missing directories in "
                                                                       + "the file's pathname. For the file consumer, that means creating the starting directory. For the file "
                                                                       + "producer, it means the directory the files should be written to.")
    protected boolean autoCreate = true;
    @UriParam(label = "advanced", defaultValue = "" + FileUtil.BUFFER_SIZE, description = "Buffer size in bytes used "
                                                                                          + "for writing files (or in case of FTP for downloading and uploading files).")
    protected int bufferSize = FileUtil.BUFFER_SIZE;
    @UriParam(description = "This option is used to specify the encoding of the file. You can use this on the "
                            + "consumer, to specify the encodings of the files, which allow Camel to know the charset it should load "
                            + "the file content in case the file content is being accessed. Likewise when writing a file, you can use "
                            + "this option to specify which charset to write the file as well. Do mind that when writing the file "
                            + "Camel may have to read the message content into memory to be able to convert the data into the "
                            + "configured charset, so do not use this if you have big messages.")
    protected String charset;
    @UriParam(javaType = "java.lang.String", description = "Use Expression such as File Language to dynamically set "
                                                           + "the filename. For consumers, it's used as a filename filter. For producers, it's used to evaluate the "
                                                           + "filename to write. If an expression is set, it take precedence over the CamelFileName header. (Note: "
                                                           + "The header itself can also be an Expression). The expression options support both String and Expression "
                                                           + "types. If the expression is a String type, it is always evaluated using the File Language. If the "
                                                           + "expression is an Expression type, the specified Expression type is used - this allows you, for "
                                                           + "instance, to use OGNL expressions. For the consumer, you can use it to filter filenames, so you can "
                                                           + "for instance consume today's file using the File Language syntax: mydata-${date:now:yyyyMMdd}.txt. The "
                                                           + "producers support the CamelOverruleFileName header which takes precedence over any existing "
                                                           + "CamelFileName header; the CamelOverruleFileName is a header that is used only once, and makes it easier "
                                                           + "as this avoids to temporary store CamelFileName and have to restore it afterwards.")
    protected Expression fileName;
    @UriParam(description = "Producer: If provided, then Camel will write a 2nd done file when the original file has "
                            + "been written. The done file will be empty. This option configures what file name to use. Either you can "
                            + "specify a fixed name. Or you can use dynamic placeholders. The done file will always be written in the "
                            + "same folder as the original file.<p/> Consumer: If provided, Camel will only consume files if a done "
                            + "file exists. This option configures what file name to use. Either you can specify a fixed name. Or you "
                            + "can use dynamic placeholders.The done file is always expected in the same folder as the original "
                            + "file.<p/> Only ${file.name} and ${file.name.next} is supported as dynamic placeholders.")
    protected String doneFileName;

    // producer options

    @UriParam(label = "producer", description = "Flatten is used to flatten the file name path to strip any leading "
                                                + "paths, so it's just the file name. This allows you to consume recursively into sub-directories, but "
                                                + "when you eg write the files to another directory they will be written in a single directory. "
                                                + "Setting this to true on the producer enforces that any file name in CamelFileName header will be "
                                                + "stripped for any leading paths.")
    protected boolean flatten;
    @UriParam(label = "producer", defaultValue = "Override", description = "What to do if a file already exists with "
                                                                           + "the same name. Override, which is the default, replaces the existing file.<p/>"
                                                                           + " - Append - adds content to the existing file.<p/> "
                                                                           + " - Fail - throws a GenericFileOperationException, indicating that there is already an existing file.<p/> "
                                                                           + " - Ignore - silently ignores the problem and does not override the existing file, "
                                                                           + "but assumes everything is okay.<p/> "
                                                                           + " - Move - option requires to use the moveExisting option to be configured as well.  The option "
                                                                           + "eagerDeleteTargetFile can be used to control what to do if an moving the file, and there "
                                                                           + "exists already an existing file, otherwise causing the move operation to fail. The Move option will move "
                                                                           + "any existing files, before writing the target file.<p/> "
                                                                           + " - TryRename is only applicable if tempFileName option is in use. This allows to try renaming the file "
                                                                           + "from the temporary name to the actual name, without doing any exists check. This check may be faster on "
                                                                           + "some file systems and especially FTP servers.")
    protected GenericFileExist fileExist = GenericFileExist.Override;
    @UriParam(label = "producer", description = "This option is used to write the file using a temporary name and "
                                                + "then, after the write is complete, rename it to the real name. Can be used to identify files being "
                                                + "written and also avoid consumers (not using exclusive read locks) reading in progress files. Is often "
                                                + "used by FTP when uploading big files.")
    protected String tempPrefix;
    @UriParam(label = "producer", javaType = "java.lang.String", description = "The same as tempPrefix option but "
                                                                               + "offering a more fine grained control on the naming of the temporary filename as it uses the File "
                                                                               + "Language. The location for tempFilename is relative to the final file location in the option "
                                                                               + "'fileName', not the target directory in the base uri. For example if option fileName includes a "
                                                                               + "directory prefix: dir/finalFilename then tempFileName is relative to that subdirectory dir.")
    protected Expression tempFileName;
    @UriParam(label = "producer,advanced", defaultValue = "true", description = "Whether or not to eagerly delete "
                                                                                + "any existing target file. This option only applies when you use fileExists=Override and the "
                                                                                + "tempFileName option as well. You can use this to disable (set it to false) deleting the target "
                                                                                + "file before the temp file is written. For example you may write big files and want the target file "
                                                                                + "to exists during the temp file is being written. This ensure the target file is only deleted until "
                                                                                + "the very last moment, just before the temp file is being renamed to the target filename. This option "
                                                                                + "is also used to control whether to delete any existing files when fileExist=Move is enabled, and an "
                                                                                + "existing file exists. If this option copyAndDeleteOnRenameFails false, then an exception will be thrown "
                                                                                + "if an existing file existed, if its true, then the existing file is deleted before the move operation.")
    protected boolean eagerDeleteTargetFile = true;
    @UriParam(label = "producer,advanced", description = "Will keep the last modified timestamp from the source file "
                                                         + "(if any). Will use the Exchange.FILE_LAST_MODIFIED header to located the timestamp. This header can "
                                                         + "contain either a java.util.Date or long with the timestamp. If the timestamp exists and the option is "
                                                         + "enabled it will set this timestamp on the written file. Note: This option only applies to the file "
                                                         + "producer. You cannot use this option with any of the ftp producers.")
    protected boolean keepLastModified;
    @UriParam(label = "producer,advanced", description = "Used to specify if a null body is allowed during file "
                                                         + "writing. If set to true then an empty file will be created, when set to false, and attempting to send"
                                                         + " a null body to the file component, a GenericFileWriteException of 'Cannot write null body to file.' "
                                                         + "will be thrown. If the `fileExist` option is set to 'Override', then the file will be truncated, and "
                                                         + "if set to `append` the file will remain unchanged.")
    protected boolean allowNullBody;
    @UriParam(label = "producer", defaultValue = "true", description = "Used for jailing (restricting) writing files "
                                                                       + "to the starting directory (and sub) only. This is enabled by default to not allow Camel to write files "
                                                                       + "to outside directories (to be more secured out of the box). You can turn this off to allow writing "
                                                                       + "files to directories outside the starting directory, such as parent or root folders.")
    protected boolean jailStartingDirectory = true;
    @UriParam(label = "producer", description = "Used to append characters (text) after writing files. This can for "
                                                + "example be used to add new lines or other separators when writing and appending to existing files. <p/> "
                                                + "To specify new-line (slash-n or slash-r) or tab (slash-t) characters then escape with an extra slash, " + "eg slash-slash-n.")
    protected String appendChars;

    // consumer options

    @UriParam
    protected GenericFileConfiguration configuration;
    @UriParam(label = "consumer,advanced", description = "A pluggable " + "org.apache.camel.component.file.GenericFileProcessStrategy "
                                                         + "allowing you to implement your own readLock option or similar. Can also be used when special conditions "
                                                         + "must be met before a file can be consumed, such as a special ready file exists. If this option is set "
                                                         + "then the readLock option does not apply.")
    protected GenericFileProcessStrategy<T> processStrategy;
    @UriParam(label = "consumer,advanced", description = "A pluggable in-progress repository "
                                                         + "org.apache.camel.spi.IdempotentRepository. The in-progress repository is used to account the current in "
                                                         + "progress files being consumed. By default a memory based repository is used.")
    protected IdempotentRepository inProgressRepository = MemoryIdempotentRepository.memoryIdempotentRepository(DEFAULT_IN_PROGRESS_CACHE_SIZE);
    @UriParam(label = "consumer,advanced", description = "When consuming, a local work directory can be used to "
                                                         + "store the remote file content directly in local files, to avoid loading the content into memory. This "
                                                         + "is beneficial, if you consume a very big remote file and thus can conserve memory.")
    protected String localWorkDirectory;
    @UriParam(label = "consumer", description = "If true, the file is not moved or deleted in any way. This option "
                                                + "is good for readonly data, or for ETL type requirements. If noop=true, Camel will set idempotent=true "
                                                + "as well, to avoid consuming the same files over and over again.")
    protected boolean noop;
    @UriParam(label = "consumer", description = "If a directory, will look for files in all the sub-directories as well.")
    protected boolean recursive;
    @UriParam(label = "consumer", description = "If true, the file will be deleted after it is processed successfully.")
    protected boolean delete;
    @UriParam(label = "consumer", description = "When pre-sort is enabled then the consumer will sort the file and "
                                                + "directory names during polling,  that was retrieved from the file system. You may want to do this in "
                                                + "case you need to operate on the files  in a sorted order. The pre-sort is executed before the consumer "
                                                + "starts to filter, and accept files  to process by Camel. This option is default=false meaning disabled.")
    protected boolean preSort;
    @UriParam(label = "consumer,filter", description = "To define a maximum messages to gather per poll. By default "
                                                       + "no maximum is set. Can be used to set a limit of e.g. 1000 to avoid when starting up the server that "
                                                       + "there are thousands of files. Set a value of 0 or negative to disabled it. Notice: If this option is "
                                                       + "in use then the File and FTP components will limit before any sorting. For example if you have 100000 "
                                                       + "files and use maxMessagesPerPoll=500, then only the first 500 files will be picked up, and then sorted. "
                                                       + "You can use the eagerMaxMessagesPerPoll option and set this to false to allow to scan all files first "
                                                       + "and then sort afterwards.")
    protected int maxMessagesPerPoll;
    @UriParam(label = "consumer,filter", defaultValue = "true", description = "Allows for controlling whether the "
                                                                              + "limit from maxMessagesPerPoll is eager or not. If eager then the limit is during the scanning of files. "
                                                                              + "Where as false would scan all files, and then perform sorting. Setting this option to false allows for "
                                                                              + "sorting all files first, and then limit the poll. Mind that this requires a higher memory usage as all "
                                                                              + "file details are in memory to perform the sorting.")
    protected boolean eagerMaxMessagesPerPoll = true;
    @UriParam(label = "consumer,filter", defaultValue = "" + Integer.MAX_VALUE, description = "The maximum depth to " + "traverse when recursively processing a directory.")
    protected int maxDepth = Integer.MAX_VALUE;
    @UriParam(label = "consumer,filter", description = "The minimum depth to start processing when recursively "
                                                       + "processing a directory. Using minDepth=1 means the base directory. Using minDepth=2 means the first " + "sub directory.")
    protected int minDepth;
    @UriParam(label = "consumer,filter", description = "Is used to include files, if filename matches the regex "
                                                       + "pattern (matching is case in-sensitive). <p/> Notice if you use symbols such as plus sign and others "
                                                       + "you would need to configure this using the RAW() syntax if configuring this as an endpoint uri. See "
                                                       + "more details at <a href=\"http://camel.apache.org/how-do-i-configure-endpoints.html\">configuring " + "endpoint uris</a>")
    protected String include;
    @UriParam(label = "consumer,filter", description = "Is used to exclude files, if filename matches the regex "
                                                       + "pattern (matching is case in-senstive). <p/> Notice if you use symbols such as plus sign and others "
                                                       + "you would need to configure this using the RAW() syntax if configuring this as an endpoint uri. See "
                                                       + "more details at <a href=\"http://camel.apache.org/how-do-i-configure-endpoints.html\">configuring " + ""
                                                       + "endpoint uris</a>")
    protected String exclude;
    @UriParam(label = "consumer,filter", javaType = "java.lang.String", description = "Expression (such as Simple "
                                                                                      + "Language) used to dynamically set the filename when moving it after processing. To move files into "
                                                                                      + "a .done subdirectory just enter .done.")
    protected Expression move;
    @UriParam(label = "consumer", javaType = "java.lang.String", description = "Sets the move failure expression "
                                                                               + "based on Simple language. For example, to move files into a .error subdirectory use: .error. Note: "
                                                                               + "When moving the files to the fail location Camel will handle the error and will not pick up the "
                                                                               + "file again.")
    protected Expression moveFailed;
    @UriParam(label = "consumer", javaType = "java.lang.String", description = "Expression (such as File Language) "
                                                                               + "used to dynamically set the filename when moving it before processing. For example to move in-progress "
                                                                               + "files into the order directory set this value to order.")
    protected Expression preMove;
    @UriParam(label = "producer", javaType = "java.lang.String", description = "Expression (such as File Language) "
                                                                               + "used to compute file name to use when fileExist=Move is configured. To move files into a backup "
                                                                               + "subdirectory just enter backup. This option only supports the following File Language tokens: "
                                                                               + "\"file:name\", \"file:name.ext\", \"file:name.noext\", \"file:onlyname\", \"file:onlyname.noext\", "
                                                                               + "\"file:ext\", and \"file:parent\". Notice the \"file:parent\" is not supported by the FTP component, "
                                                                               + "as the FTP component can only move any existing files to a relative directory based on current dir "
                                                                               + "as base.")
    protected Expression moveExisting;
    @UriParam(label = "producer,advanced", description = "Strategy (Custom Strategy) used to move file with special "
                                                         + "naming token to use when fileExist=Move is configured. By default, there is an implementation used if "
                                                         + "no custom strategy is provided")
    protected FileMoveExistingStrategy moveExistingFileStrategy;
    @UriParam(label = "consumer,filter", defaultValue = "false", description = "Option to use the Idempotent "
                                                                               + "Consumer EIP pattern to let Camel skip already processed files. Will by default use a memory based "
                                                                               + "LRUCache that holds 1000 entries. If noop=true then idempotent will be enabled as well to avoid "
                                                                               + "consuming the same files over and over again.")
    protected Boolean idempotent;
    @UriParam(label = "consumer,filter", javaType = "java.lang.String", description = "To use a custom idempotent "
                                                                                      + "key. By default the absolute path of the file is used. You can use the File Language, for example to "
                                                                                      + "use the file name and file size, you can do: idempotentKey=${file:name}-${file:size}")
    protected Expression idempotentKey;
    @UriParam(label = "consumer,filter", description = "A pluggable repository org.apache.camel.spi.IdempotentRepository "
                                                       + "which by default use MemoryMessageIdRepository if none is specified and idempotent is true.")
    protected IdempotentRepository idempotentRepository;
    @UriParam(label = "consumer,filter", description = "Pluggable filter as a org.apache.camel.component.file.GenericFileFilter "
                                                       + "class. Will skip files if filter returns false in its accept() method.")
    protected GenericFileFilter<T> filter;
    @UriParam(label = "consumer,filter", javaType = "java.lang.String", description = "Filters the directory based on "
                                                                                      + "Simple language. For example to filter on current date, you can use a simple date pattern such as "
                                                                                      + "${date:now:yyyMMdd}")
    protected Predicate filterDirectory;
    @UriParam(label = "consumer,filter", javaType = "java.lang.String", description = "Filters the file based on "
                                                                                      + "Simple language. For example to filter on file size, you can use ${file:size} > 5000")
    protected Predicate filterFile;
    @UriParam(label = "consumer,filter", defaultValue = "true", description = "Sets case sensitive flag on ant filter.")
    protected boolean antFilterCaseSensitive = true;
    protected volatile AntPathMatcherGenericFileFilter<T> antFilter;
    @UriParam(label = "consumer,filter", description = "Ant style filter inclusion. Multiple inclusions may be " + "specified in comma-delimited format.")
    protected String antInclude;
    @UriParam(label = "consumer,filter", description = "Ant style filter exclusion. If both antInclude and antExclude are used, antExclude takes precedence over antInclude. Multiple exclusions may be specified in comma-delimited format.")
    protected String antExclude;
    @UriParam(label = "consumer,sort", description = "Pluggable sorter as a " + "java.util.Comparator<org.apache.camel.component.file.GenericFile> class.")
    protected Comparator<GenericFile<T>> sorter;
    @UriParam(label = "consumer,sort", javaType = "java.lang.String", description = "Built-in sort by using the "
                                                                                    + "File Language. Supports nested sorts, so you can have a sort by file name and as a 2nd group sort "
                                                                                    + "by modified date.")
    protected Comparator<Exchange> sortBy;
    @UriParam(label = "consumer,sort", description = "To shuffle the list of files (sort in random order)")
    protected boolean shuffle;
    @UriParam(label = "consumer,lock", defaultValue = "none", enums = "none,markerFile,fileLock,rename,changed,idempotent,idempotent-changed,idempotent-rename", 
              description = "Used by consumer, to only poll the files if it has exclusive read-lock on the file (i.e. "
                            + "the file is not in-progress or being written). Camel will wait until the file lock is granted. "
                            + "This option provides the build in strategies:<p/>"
                            + " - none - No read lock is in use<p/>"
                            + " - markerFile - Camel creates a marker file (fileName.camelLock) and then holds a lock on it. "
                            + "This option is not available for the FTP component<p/>"
                            + " - changed - Changed is using file length/modification timestamp to detect whether the file "
                            + "is currently being copied or not. Will at least use 1 sec to determine this, so this option "
                            + "cannot consume files as fast as the others, but can be more reliable as the JDK IO API "
                            + "cannot always determine whether a file is currently being used by another process. The option "
                            + "readLockCheckInterval can be used to set the check frequency.<p/>"
                            + " - fileLock - is for using java.nio.channels.FileLock. This option is not avail for Windows OS "
                            + "and the FTP component. This approach should be avoided when accessing a remote file system via "
                            + "a mount/share unless that file system supports distributed file locks.<p/>"
                            + " - rename - rename is for using a try to rename the file as a test if we can get exclusive "
                            + "read-lock.<p/>"
                            + " - idempotent - (only for file component) idempotent is for using a idempotentRepository "
                            + "as the read-lock. This allows to use read locks that supports clustering if the idempotent "
                            + "repository implementation supports that.<p/>"
                            + " - idempotent-changed - (only for file component) idempotent-changed is for using a "
                            + "idempotentRepository and changed as the combined read-lock. This allows to use read locks "
                            + "that supports clustering if the idempotent repository implementation supports that.<p/>"
                            + " - idempotent-rename - (only for file component) idempotent-rename is for using a "
                            + "idempotentRepository and rename as the combined read-lock. This allows to use read locks "
                            + "that supports clustering if the idempotent repository implementation supports that.<p/>"
                            + "Notice: The various read locks is not all suited to work in clustered mode, where concurrent "
                            + "consumers on different nodes is competing for the same files on a shared file system. The "
                            + "markerFile using a close to atomic operation to create the empty marker file, but its not "
                            + "guaranteed to work in a cluster. The fileLock may work better but then the file system need "
                            + "to support distributed file locks, and so on. Using the idempotent read lock can support "
                            + "clustering if the idempotent repository supports clustering, such as Hazelcast Component or "
                            + "Infinispan.")
    
    protected String readLock = "none";
    @UriParam(label = "consumer,lock", defaultValue = "1000", description = "Interval in millis for the read-lock, "
                                                                            + "if supported by the read lock. This interval is used for sleeping between attempts to acquire the read "
                                                                            + "lock. For example when using the changed read lock, you can set a higher interval period to cater for "
                                                                            + "slow writes. The default of 1 sec. may be too fast if the producer is very slow writing the file. <p/>"
                                                                            + "Notice: For FTP the default readLockCheckInterval is 5000. <p/> The readLockTimeout value must be "
                                                                            + "higher than readLockCheckInterval, but a rule of thumb is to have a timeout that is at least 2 or more "
                                                                            + "times higher than the readLockCheckInterval. This is needed to ensure that amble time is allowed for "
                                                                            + "the read lock process to try to grab the lock before the timeout was hit.")
    protected long readLockCheckInterval = 1000;
    @UriParam(label = "consumer,lock", defaultValue = "10000", description = "Optional timeout in millis for the "
                                                                             + "read-lock, if supported by the read-lock. If the read-lock could not be granted and the timeout "
                                                                             + "triggered, then Camel will skip the file. At next poll Camel, will try the file again, and this time "
                                                                             + "maybe the read-lock could be granted. Use a value of 0 or lower to indicate forever. Currently "
                                                                             + "fileLock, changed and rename support the timeout. <p/> Notice: For FTP the default readLockTimeout "
                                                                             + "value is 20000 instead of 10000. <p/> The readLockTimeout value must be higher than "
                                                                             + "readLockCheckInterval, but a rule of thumb is to have a timeout that is at least 2 or more times "
                                                                             + "higher than the readLockCheckInterval. This is needed to ensure that amble time is allowed for the "
                                                                             + "read lock process to try to grab the lock before the timeout was hit.")
    protected long readLockTimeout = 10000;
    @UriParam(label = "consumer,lock", defaultValue = "true", description = "Whether to use marker file with the "
                                                                            + "changed, rename, or exclusive read lock types. By default a marker file is used as well to guard "
                                                                            + "against other processes picking up the same files. This behavior can be turned off by setting this "
                                                                            + "option to false. For example if you do not want to write marker files to the file systems by the "
                                                                            + "Camel application.")
    protected boolean readLockMarkerFile = true;
    @UriParam(label = "consumer,lock", defaultValue = "true", description = "Whether or not read lock with marker "
                                                                            + "files should upon startup delete any orphan read lock files, which may have been left on the file "
                                                                            + "system, if Camel was not properly shutdown (such as a JVM crash). <p/> If turning this option to "
                                                                            + "<tt>false</tt> then any orphaned lock file will cause Camel to not attempt to pickup that file, this "
                                                                            + "could also be due another node is concurrently reading files from the same shared directory.")
    protected boolean readLockDeleteOrphanLockFiles = true;
    @UriParam(label = "consumer,lock", defaultValue = "DEBUG", description = "Logging level used when a read lock "
                                                                             + "could not be acquired. By default a DEBUG is logged. You can change this level, for example to OFF to "
                                                                             + "not have any logging. This option is only applicable for readLock of types: changed, fileLock, "
                                                                             + "idempotent, idempotent-changed, idempotent-rename, rename.")
    protected LoggingLevel readLockLoggingLevel = LoggingLevel.DEBUG;
    @UriParam(label = "consumer,lock", defaultValue = "1", description = "This option is applied only for "
                                                                         + "readLock=changed. It allows you to configure a minimum file length. By default Camel expects the file "
                                                                         + "to contain data, and thus the default value is 1. You can set this option to zero, to allow consuming "
                                                                         + "zero-length files.")
    protected long readLockMinLength = 1;
    @UriParam(label = "consumer,lock", defaultValue = "0", description = "This option is applied only for "
                                                                         + "readLock=changed. It allows to specify a minimum age the file must be before attempting to acquire "
                                                                         + "the read lock. For example use readLockMinAge=300s to require the file is at last 5 minutes old. This "
                                                                         + "can speedup the changed read lock as it will only attempt to acquire files which are at least "
                                                                         + "that given age.")
    protected long readLockMinAge;
    @UriParam(label = "consumer,lock", defaultValue = "true", description = "This option is applied only for "
                                                                            + "readLock=idempotent. It allows to specify whether to remove the file name entry from the idempotent "
                                                                            + "repository when processing the file failed and a rollback happens. If this option is false, then the "
                                                                            + "file name entry is confirmed (as if the file did a commit).")
    protected boolean readLockRemoveOnRollback = true;
    @UriParam(label = "consumer,lock", description = "This option is applied only for readLock=idempotent. It allows "
                                                     + "to specify whether to remove the file name entry from the idempotent repository when processing the "
                                                     + "file is succeeded and a commit happens. <p/> By default the file is not removed which ensures that "
                                                     + "any race-condition do not occur so another active node may attempt to grab the file. Instead the "
                                                     + "idempotent repository may support eviction strategies that you can configure to evict the file name "
                                                     + "entry after X minutes - this ensures no problems with race conditions. <p/> See more details at the "
                                                     + "readLockIdempotentReleaseDelay option.")
    protected boolean readLockRemoveOnCommit;
    @UriParam(label = "consumer,lock", description = "Whether to delay the release task for a period of millis. <p/> "
                                                     + "This can be used to delay the release tasks to expand the window when a file is regarded as "
                                                     + "read-locked, in an active/active cluster scenario with a shared idempotent repository, to ensure "
                                                     + "other nodes cannot potentially scan and acquire the same file, due to race-conditions. By expanding "
                                                     + "the time-window of the release tasks helps prevents these situations. Note delaying is only needed "
                                                     + "if you have configured readLockRemoveOnCommit to true.")
    protected int readLockIdempotentReleaseDelay;
    @UriParam(label = "consumer,lock", description = "Whether the delayed release task should be synchronous or "
                                                     + "asynchronous. <p/> See more details at the readLockIdempotentReleaseDelay option.")
    protected boolean readLockIdempotentReleaseAsync;
    @UriParam(label = "consumer,lock", description = "The number of threads in the scheduled thread pool when using "
                                                     + "asynchronous release tasks. Using a default of 1 core threads should be sufficient in almost all "
                                                     + "use-cases, only set this to a higher value if either updating the idempotent repository is slow, or "
                                                     + "there are a lot of files to process. This option is not in-use if you use a shared thread pool by "
                                                     + "configuring the readLockIdempotentReleaseExecutorService option. <p/> See more details at the "
                                                     + "readLockIdempotentReleaseDelay option.")
    protected int readLockIdempotentReleaseAsyncPoolSize;
    @UriParam(label = "consumer,lock", description = "To use a custom and shared thread pool for asynchronous "
                                                     + "release tasks. <p/> See more details at the readLockIdempotentReleaseDelay option.")
    protected ScheduledExecutorService readLockIdempotentReleaseExecutorService;
    @UriParam(label = "consumer,lock", description = "Pluggable read-lock as a org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy implementation.")
    protected GenericFileExclusiveReadLockStrategy<T> exclusiveReadLockStrategy;
    @UriParam(label = "consumer,advanced", description = "To use a custom " + "{@link org.apache.camel.spi.ExceptionHandler} to handle any thrown exceptions that happens during "
                                                         + "the file on completion process where the consumer does either a commit or rollback. The default "
                                                         + "implementation will log any exception at WARN level and ignore.")
    protected ExceptionHandler onCompletionExceptionHandler;

    private Pattern includePattern;
    private Pattern excludePattern;

    public GenericFileEndpoint() {
    }

    public GenericFileEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public abstract GenericFileConsumer<T> createConsumer(Processor processor) throws Exception;

    @Override
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

    /**
     * This implementation will <b>not</b> load the file content. Any file
     * locking is neither in use by this implementation..
     */
    @Override
    public List<Exchange> getExchanges() {
        final List<Exchange> answer = new ArrayList<>();

        GenericFileConsumer<?> consumer = null;
        try {
            // create a new consumer which can poll the exchanges we want to
            // browse
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
            // invoke poll which performs the custom processing, so we can
            // browse the exchanges
            consumer.poll();
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        } finally {
            try {
                ServiceHelper.stopService(consumer);
            } catch (Exception e) {
                LOG.debug("Error stopping consumer used for browsing exchanges. This exception will be ignored", e);
            }
        }

        return answer;
    }

    /**
     * A strategy method to lazily create the file strategy
     */
    @SuppressWarnings("unchecked")
    protected GenericFileProcessStrategy<T> createGenericFileStrategy() {
        FactoryFinder finder = getCamelContext().adapt(ExtendedCamelContext.class).getFactoryFinder("META-INF/services/org/apache/camel/component/");
        LOG.trace("Using FactoryFinder: {}", finder);
        Class<?> factory = finder.findClass(getScheme(), "strategy.factory.", CamelContext.class).orElse(null);

        if (factory == null) {
            // use default
            try {
                LOG.trace("Using ClassResolver to resolve class: {}", DEFAULT_STRATEGYFACTORY_CLASS);
                factory = this.getCamelContext().getClassResolver().resolveClass(DEFAULT_STRATEGYFACTORY_CLASS);
            } catch (Exception e) {
                LOG.trace("Cannot load class: {}", DEFAULT_STRATEGYFACTORY_CLASS, e);
            }
            // fallback and us this class loader
            try {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Using classloader: {} to resolve class: {}", this.getClass().getClassLoader(), DEFAULT_STRATEGYFACTORY_CLASS);
                }
                factory = this.getCamelContext().getClassResolver().resolveClass(DEFAULT_STRATEGYFACTORY_CLASS, this.getClass().getClassLoader());
            } catch (Exception e) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Cannot load class: {} using classloader: " + this.getClass().getClassLoader(), DEFAULT_STRATEGYFACTORY_CLASS, e);
                }
            }

            if (factory == null) {
                throw new TypeNotPresentException(DEFAULT_STRATEGYFACTORY_CLASS + " class not found", null);
            }
        }

        try {
            Method factoryMethod = factory.getMethod("createGenericFileProcessStrategy", CamelContext.class, Map.class);
            Map<String, Object> params = getParamsAsMap();
            LOG.debug("Parameters for Generic file process strategy {}", params);
            return (GenericFileProcessStrategy<T>)ObjectHelper.invokeMethod(factoryMethod, null, getCamelContext(), params);
        } catch (NoSuchMethodException e) {
            throw new TypeNotPresentException(factory.getSimpleName() + ".createGenericFileProcessStrategy method not found", e);
        }
    }

    public boolean isNoop() {
        return noop;
    }

    /**
     * If true, the file is not moved or deleted in any way. This option is good
     * for readonly data, or for ETL type requirements. If noop=true, Camel will
     * set idempotent=true as well, to avoid consuming the same files over and
     * over again.
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
     * Is used to include files, if filename matches the regex pattern (matching
     * is case in-sensitive).
     * <p/>
     * Notice if you use symbols such as plus sign and others you would need to
     * configure this using the RAW() syntax if configuring this as an endpoint
     * uri. See more details at <a href=
     * "http://camel.apache.org/how-do-i-configure-endpoints.html">configuring
     * endpoint uris</a>
     */
    public void setInclude(String include) {
        this.include = include;
        this.includePattern = Pattern.compile(include, Pattern.CASE_INSENSITIVE);
    }

    public Pattern getIncludePattern() {
        return includePattern;
    }

    public String getExclude() {
        return exclude;
    }

    /**
     * Is used to exclude files, if filename matches the regex pattern (matching
     * is case in-senstive).
     * <p/>
     * Notice if you use symbols such as plus sign and others you would need to
     * configure this using the RAW() syntax if configuring this as an endpoint
     * uri. See more details at <a href=
     * "http://camel.apache.org/how-do-i-configure-endpoints.html">configuring
     * endpoint uris</a>
     */
    public void setExclude(String exclude) {
        this.exclude = exclude;
        this.excludePattern = Pattern.compile(exclude, Pattern.CASE_INSENSITIVE);
    }

    public Pattern getExcludePattern() {
        return this.excludePattern;
    }

    public String getAntInclude() {
        return antInclude;
    }

    /**
     * Ant style filter inclusion. Multiple inclusions may be specified in
     * comma-delimited format.
     */
    public void setAntInclude(String antInclude) {
        this.antInclude = antInclude;
    }

    public String getAntExclude() {
        return antExclude;
    }

    /**
     * Ant style filter exclusion. If both antInclude and antExclude are used,
     * antExclude takes precedence over antInclude. Multiple exclusions may be
     * specified in comma-delimited format.
     */
    public void setAntExclude(String antExclude) {
        this.antExclude = antExclude;
    }

    public boolean isAntFilterCaseSensitive() {
        return antFilterCaseSensitive;
    }

    /**
     * Sets case sensitive flag on ant filter
     */
    public void setAntFilterCaseSensitive(boolean antFilterCaseSensitive) {
        this.antFilterCaseSensitive = antFilterCaseSensitive;
    }

    public GenericFileFilter<T> getAntFilter() {
        return antFilter;
    }

    public boolean isPreSort() {
        return preSort;
    }

    /**
     * When pre-sort is enabled then the consumer will sort the file and
     * directory names during polling, that was retrieved from the file system.
     * You may want to do this in case you need to operate on the files in a
     * sorted order. The pre-sort is executed before the consumer starts to
     * filter, and accept files to process by Camel. This option is
     * default=false meaning disabled.
     */
    public void setPreSort(boolean preSort) {
        this.preSort = preSort;
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
     * Flatten is used to flatten the file name path to strip any leading paths,
     * so it's just the file name. This allows you to consume recursively into
     * sub-directories, but when you eg write the files to another directory
     * they will be written in a single directory. Setting this to true on the
     * producer enforces that any file name in CamelFileName header will be
     * stripped for any leading paths.
     */
    public void setFlatten(boolean flatten) {
        this.flatten = flatten;
    }

    public Expression getMove() {
        return move;
    }

    /**
     * Expression (such as Simple Language) used to dynamically set the filename
     * when moving it after processing. To move files into a .done subdirectory
     * just enter .done.
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
     * Sets the move failure expression based on Simple language. For example,
     * to move files into a .error subdirectory use: .error. Note: When moving
     * the files to the fail location Camel will handle the error and will not
     * pick up the file again.
     */
    public void setMoveFailed(Expression moveFailed) {
        this.moveFailed = moveFailed;
    }

    public void setMoveFailed(String fileLanguageExpression) {
        String expression = configureMoveOrPreMoveExpression(fileLanguageExpression);
        this.moveFailed = createFileLanguageExpression(expression);
    }

    public Predicate getFilterDirectory() {
        return filterDirectory;
    }

    /**
     * Filters the directory based on Simple language. For example to filter on
     * current date, you can use a simple date pattern such as
     * ${date:now:yyyMMdd}
     */
    public void setFilterDirectory(Predicate filterDirectory) {
        this.filterDirectory = filterDirectory;
    }

    /**
     * Filters the directory based on Simple language. For example to filter on
     * current date, you can use a simple date pattern such as
     * ${date:now:yyyMMdd}
     * 
     * @see #setFilterDirectory(Predicate)
     */
    public void setFilterDirectory(String expression) {
        this.filterDirectory = createFileLanguagePredicate(expression);
    }

    public Predicate getFilterFile() {
        return filterFile;
    }

    /**
     * Filters the file based on Simple language. For example to filter on file
     * size, you can use ${file:size} > 5000
     */
    public void setFilterFile(Predicate filterFile) {
        this.filterFile = filterFile;
    }

    /**
     * Filters the file based on Simple language. For example to filter on file
     * size, you can use ${file:size} > 5000
     * 
     * @see #setFilterFile(Predicate)
     */
    public void setFilterFile(String expression) {
        this.filterFile = createFileLanguagePredicate(expression);
    }

    public Expression getPreMove() {
        return preMove;
    }

    /**
     * Expression (such as File Language) used to dynamically set the filename
     * when moving it before processing. For example to move in-progress files
     * into the order directory set this value to order.
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
     * Expression (such as File Language) used to compute file name to use when
     * fileExist=Move is configured. To move files into a backup subdirectory
     * just enter backup. This option only supports the following File Language
     * tokens: "file:name", "file:name.ext", "file:name.noext", "file:onlyname",
     * "file:onlyname.noext", "file:ext", and "file:parent". Notice the
     * "file:parent" is not supported by the FTP component, as the FTP component
     * can only move any existing files to a relative directory based on current
     * dir as base.
     */
    public void setMoveExisting(Expression moveExisting) {
        this.moveExisting = moveExisting;
    }

    public FileMoveExistingStrategy getMoveExistingFileStrategy() {
        return moveExistingFileStrategy;
    }

    /**
     * Strategy (Custom Strategy) used to move file with special naming token to
     * use when fileExist=Move is configured. By default, there is an
     * implementation used if no custom strategy is provided
     */
    public void setMoveExistingFileStrategy(FileMoveExistingStrategy moveExistingFileStrategy) {
        this.moveExistingFileStrategy = moveExistingFileStrategy;
    }

    public void setMoveExisting(String fileLanguageExpression) {
        String expression = configureMoveOrPreMoveExpression(fileLanguageExpression);
        this.moveExisting = createFileLanguageExpression(expression);
    }

    public Expression getFileName() {
        return fileName;
    }

    /**
     * Use Expression such as File Language to dynamically set the filename. For
     * consumers, it's used as a filename filter. For producers, it's used to
     * evaluate the filename to write. If an expression is set, it take
     * precedence over the CamelFileName header. (Note: The header itself can
     * also be an Expression). The expression options support both String and
     * Expression types. If the expression is a String type, it is always
     * evaluated using the File Language. If the expression is an Expression
     * type, the specified Expression type is used - this allows you, for
     * instance, to use OGNL expressions. For the consumer, you can use it to
     * filter filenames, so you can for instance consume today's file using the
     * File Language syntax: mydata-${date:now:yyyyMMdd}.txt. The producers
     * support the CamelOverruleFileName header which takes precedence over any
     * existing CamelFileName header; the CamelOverruleFileName is a header that
     * is used only once, and makes it easier as this avoids to temporary store
     * CamelFileName and have to restore it afterwards.
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
     * Producer: If provided, then Camel will write a 2nd done file when the
     * original file has been written. The done file will be empty. This option
     * configures what file name to use. Either you can specify a fixed name. Or
     * you can use dynamic placeholders. The done file will always be written in
     * the same folder as the original file.
     * <p/>
     * Consumer: If provided, Camel will only consume files if a done file
     * exists. This option configures what file name to use. Either you can
     * specify a fixed name. Or you can use dynamic placeholders.The done file
     * is always expected in the same folder as the original file.
     * <p/>
     * Only ${file.name} and ${file.name.noext} is supported as dynamic
     * placeholders.
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
     * This option is used to specify the encoding of the file. You can use this
     * on the consumer, to specify the encodings of the files, which allow Camel
     * to know the charset it should load the file content in case the file
     * content is being accessed. Likewise when writing a file, you can use this
     * option to specify which charset to write the file as well. Do mind that
     * when writing the file Camel may have to read the message content into
     * memory to be able to convert the data into the configured charset, so do
     * not use this if you have big messages.
     */
    public void setCharset(String charset) {
        IOHelper.validateCharset(charset);
        this.charset = charset;
    }

    protected boolean isIdempotentSet() {
        return idempotent != null;
    }

    public Boolean getIdempotent() {
        return idempotent;
    }

    /**
     * Option to use the Idempotent Consumer EIP pattern to let Camel skip
     * already processed files. Will by default use a memory based LRUCache that
     * holds 1000 entries. If noop=true then idempotent will be enabled as well
     * to avoid consuming the same files over and over again.
     */
    public void setIdempotent(Boolean idempotent) {
        this.idempotent = idempotent;
    }

    public Expression getIdempotentKey() {
        return idempotentKey;
    }

    /**
     * To use a custom idempotent key. By default the absolute path of the file
     * is used. You can use the File Language, for example to use the file name
     * and file size, you can do: idempotentKey=${file:name}-${file:size}
     */
    public void setIdempotentKey(Expression idempotentKey) {
        this.idempotentKey = idempotentKey;
    }

    public void setIdempotentKey(String expression) {
        this.idempotentKey = createFileLanguageExpression(expression);
    }

    public IdempotentRepository getIdempotentRepository() {
        return idempotentRepository;
    }

    /**
     * A pluggable repository org.apache.camel.spi.IdempotentRepository which by
     * default use MemoryMessageIdRepository if none is specified and idempotent
     * is true.
     */
    public void setIdempotentRepository(IdempotentRepository idempotentRepository) {
        this.idempotentRepository = idempotentRepository;
    }

    public GenericFileFilter<T> getFilter() {
        return filter;
    }

    /**
     * Pluggable filter as a org.apache.camel.component.file.GenericFileFilter
     * class. Will skip files if filter returns false in its accept() method.
     */
    public void setFilter(GenericFileFilter<T> filter) {
        this.filter = filter;
    }

    public Comparator<GenericFile<T>> getSorter() {
        return sorter;
    }

    /**
     * Pluggable sorter as a
     * java.util.Comparator<org.apache.camel.component.file.GenericFile> class.
     */
    public void setSorter(Comparator<GenericFile<T>> sorter) {
        this.sorter = sorter;
    }

    public Comparator<Exchange> getSortBy() {
        return sortBy;
    }

    /**
     * Built-in sort by using the File Language. Supports nested sorts, so you
     * can have a sort by file name and as a 2nd group sort by modified date.
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

    public boolean isShuffle() {
        return shuffle;
    }

    /**
     * To shuffle the list of files (sort in random order)
     */
    public void setShuffle(boolean shuffle) {
        this.shuffle = shuffle;
    }

    public String getTempPrefix() {
        return tempPrefix;
    }

    /**
     * This option is used to write the file using a temporary name and then,
     * after the write is complete, rename it to the real name. Can be used to
     * identify files being written and also avoid consumers (not using
     * exclusive read locks) reading in progress files. Is often used by FTP
     * when uploading big files.
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
     * The same as tempPrefix option but offering a more fine grained control on
     * the naming of the temporary filename as it uses the File Language. The
     * location for tempFilename is relative to the final file location in the
     * option 'fileName', not the target directory in the base uri. For example
     * if option fileName includes a directory prefix: dir/finalFilename then
     * tempFileName is relative to that subdirectory dir.
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
     * Whether or not to eagerly delete any existing target file. This option
     * only applies when you use fileExists=Override and the tempFileName option
     * as well. You can use this to disable (set it to false) deleting the
     * target file before the temp file is written. For example you may write
     * big files and want the target file to exists during the temp file is
     * being written. This ensure the target file is only deleted until the very
     * last moment, just before the temp file is being renamed to the target
     * filename. This option is also used to control whether to delete any
     * existing files when fileExist=Move is enabled, and an existing file
     * exists. If this option copyAndDeleteOnRenameFails false, then an
     * exception will be thrown if an existing file existed, if its true, then
     * the existing file is deleted before the move operation.
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
     * Pluggable read-lock as a
     * org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy
     * implementation.
     */
    public void setExclusiveReadLockStrategy(GenericFileExclusiveReadLockStrategy<T> exclusiveReadLockStrategy) {
        this.exclusiveReadLockStrategy = exclusiveReadLockStrategy;
    }

    public String getReadLock() {
        return readLock;
    }

    /**
     * Used by consumer, to only poll the files if it has exclusive read-lock on
     * the file (i.e. the file is not in-progress or being written). Camel will
     * wait until the file lock is granted. This option provides the build in
     * strategies:\n\n - none - No read lock is in use\n - markerFile - Camel
     * creates a marker file (fileName.camelLock) and then holds a lock on it.
     * This option is not available for the FTP component\n - changed - Changed
     * is using file length/modification timestamp to detect whether the file is
     * currently being copied or not. Will at least use 1 sec to determine this,
     * so this option cannot consume files as fast as the others, but can be
     * more reliable as the JDK IO API cannot always determine whether a file is
     * currently being used by another process. The option readLockCheckInterval
     * can be used to set the check frequency.\n - fileLock - is for using
     * java.nio.channels.FileLock. This option is not avail for Windows OS and
     * the FTP component. This approach should be avoided when accessing a
     * remote file system via a mount/share unless that file system supports
     * distributed file locks.\n - rename - rename is for using a try to rename
     * the file as a test if we can get exclusive read-lock.\n - idempotent -
     * (only for file component) idempotent is for using a idempotentRepository
     * as the read-lock. This allows to use read locks that supports clustering
     * if the idempotent repository implementation supports that.\n -
     * idempotent-changed - (only for file component) idempotent-changed is for
     * using a idempotentRepository and changed as the combined read-lock. This
     * allows to use read locks that supports clustering if the idempotent
     * repository implementation supports that.\n - idempotent-rename - (only
     * for file component) idempotent-rename is for using a idempotentRepository
     * and rename as the combined read-lock. This allows to use read locks that
     * supports clustering if the idempotent repository implementation supports
     * that.\n \nNotice: The various read locks is not all suited to work in
     * clustered mode, where concurrent consumers on different nodes is
     * competing for the same files on a shared file system. The markerFile
     * using a close to atomic operation to create the empty marker file, but
     * its not guaranteed to work in a cluster. The fileLock may work better but
     * then the file system need to support distributed file locks, and so on.
     * Using the idempotent read lock can support clustering if the idempotent
     * repository supports clustering, such as Hazelcast Component or
     * Infinispan.
     */
    public void setReadLock(String readLock) {
        this.readLock = readLock;
    }

    public long getReadLockCheckInterval() {
        return readLockCheckInterval;
    }

    /**
     * Interval in millis for the read-lock, if supported by the read lock. This
     * interval is used for sleeping between attempts to acquire the read lock.
     * For example when using the changed read lock, you can set a higher
     * interval period to cater for slow writes. The default of 1 sec. may be
     * too fast if the producer is very slow writing the file.
     * <p/>
     * Notice: For FTP the default readLockCheckInterval is 5000.
     * <p/>
     * The readLockTimeout value must be higher than readLockCheckInterval, but
     * a rule of thumb is to have a timeout that is at least 2 or more times
     * higher than the readLockCheckInterval. This is needed to ensure that
     * amble time is allowed for the read lock process to try to grab the lock
     * before the timeout was hit.
     */
    public void setReadLockCheckInterval(long readLockCheckInterval) {
        this.readLockCheckInterval = readLockCheckInterval;
    }

    public long getReadLockTimeout() {
        return readLockTimeout;
    }

    /**
     * Optional timeout in millis for the read-lock, if supported by the
     * read-lock. If the read-lock could not be granted and the timeout
     * triggered, then Camel will skip the file. At next poll Camel, will try
     * the file again, and this time maybe the read-lock could be granted. Use a
     * value of 0 or lower to indicate forever. Currently fileLock, changed and
     * rename support the timeout.
     * <p/>
     * Notice: For FTP the default readLockTimeout value is 20000 instead of
     * 10000.
     * <p/>
     * The readLockTimeout value must be higher than readLockCheckInterval, but
     * a rule of thumb is to have a timeout that is at least 2 or more times
     * higher than the readLockCheckInterval. This is needed to ensure that
     * amble time is allowed for the read lock process to try to grab the lock
     * before the timeout was hit.
     */
    public void setReadLockTimeout(long readLockTimeout) {
        this.readLockTimeout = readLockTimeout;
    }

    public boolean isReadLockMarkerFile() {
        return readLockMarkerFile;
    }

    /**
     * Whether to use marker file with the changed, rename, or exclusive read
     * lock types. By default a marker file is used as well to guard against
     * other processes picking up the same files. This behavior can be turned
     * off by setting this option to false. For example if you do not want to
     * write marker files to the file systems by the Camel application.
     */
    public void setReadLockMarkerFile(boolean readLockMarkerFile) {
        this.readLockMarkerFile = readLockMarkerFile;
    }

    public boolean isReadLockDeleteOrphanLockFiles() {
        return readLockDeleteOrphanLockFiles;
    }

    /**
     * Whether or not read lock with marker files should upon startup delete any
     * orphan read lock files, which may have been left on the file system, if
     * Camel was not properly shutdown (such as a JVM crash).
     * <p/>
     * If turning this option to <tt>false</tt> then any orphaned lock file will
     * cause Camel to not attempt to pickup that file, this could also be due
     * another node is concurrently reading files from the same shared
     * directory.
     */
    public void setReadLockDeleteOrphanLockFiles(boolean readLockDeleteOrphanLockFiles) {
        this.readLockDeleteOrphanLockFiles = readLockDeleteOrphanLockFiles;
    }

    public LoggingLevel getReadLockLoggingLevel() {
        return readLockLoggingLevel;
    }

    /**
     * Logging level used when a read lock could not be acquired. By default a
     * DEBUG is logged. You can change this level, for example to OFF to not
     * have any logging. This option is only applicable for readLock of types:
     * changed, fileLock, idempotent, idempotent-changed, idempotent-rename,
     * rename.
     */
    public void setReadLockLoggingLevel(LoggingLevel readLockLoggingLevel) {
        this.readLockLoggingLevel = readLockLoggingLevel;
    }

    public long getReadLockMinLength() {
        return readLockMinLength;
    }

    /**
     * This option is applied only for readLock=changed. It allows you to
     * configure a minimum file length. By default Camel expects the file to
     * contain data, and thus the default value is 1. You can set this option to
     * zero, to allow consuming zero-length files.
     */
    public void setReadLockMinLength(long readLockMinLength) {
        this.readLockMinLength = readLockMinLength;
    }

    public long getReadLockMinAge() {
        return readLockMinAge;
    }

    /**
     * This option is applied only for readLock=changed. It allows to specify a
     * minimum age the file must be before attempting to acquire the read lock.
     * For example use readLockMinAge=300s to require the file is at last 5
     * minutes old. This can speedup the changed read lock as it will only
     * attempt to acquire files which are at least that given age.
     */
    public void setReadLockMinAge(long readLockMinAge) {
        this.readLockMinAge = readLockMinAge;
    }

    public boolean isReadLockRemoveOnRollback() {
        return readLockRemoveOnRollback;
    }

    /**
     * This option is applied only for readLock=idempotent. It allows to specify
     * whether to remove the file name entry from the idempotent repository when
     * processing the file failed and a rollback happens. If this option is
     * false, then the file name entry is confirmed (as if the file did a
     * commit).
     */
    public void setReadLockRemoveOnRollback(boolean readLockRemoveOnRollback) {
        this.readLockRemoveOnRollback = readLockRemoveOnRollback;
    }

    public boolean isReadLockRemoveOnCommit() {
        return readLockRemoveOnCommit;
    }

    /**
     * This option is applied only for readLock=idempotent. It allows to specify
     * whether to remove the file name entry from the idempotent repository when
     * processing the file is succeeded and a commit happens.
     * <p/>
     * By default the file is not removed which ensures that any race-condition
     * do not occur so another active node may attempt to grab the file. Instead
     * the idempotent repository may support eviction strategies that you can
     * configure to evict the file name entry after X minutes - this ensures no
     * problems with race conditions.
     * <p/>
     * See more details at the readLockIdempotentReleaseDelay option.
     */
    public void setReadLockRemoveOnCommit(boolean readLockRemoveOnCommit) {
        this.readLockRemoveOnCommit = readLockRemoveOnCommit;
    }

    public int getReadLockIdempotentReleaseDelay() {
        return readLockIdempotentReleaseDelay;
    }

    /**
     * Whether to delay the release task for a period of millis.
     * <p/>
     * This can be used to delay the release tasks to expand the window when a
     * file is regarded as read-locked, in an active/active cluster scenario
     * with a shared idempotent repository, to ensure other nodes cannot
     * potentially scan and acquire the same file, due to race-conditions. By
     * expanding the time-window of the release tasks helps prevents these
     * situations. Note delaying is only needed if you have configured
     * readLockRemoveOnCommit to true.
     */
    public void setReadLockIdempotentReleaseDelay(int readLockIdempotentReleaseDelay) {
        this.readLockIdempotentReleaseDelay = readLockIdempotentReleaseDelay;
    }

    public boolean isReadLockIdempotentReleaseAsync() {
        return readLockIdempotentReleaseAsync;
    }

    /**
     * Whether the delayed release task should be synchronous or asynchronous.
     * <p/>
     * See more details at the readLockIdempotentReleaseDelay option.
     */
    public void setReadLockIdempotentReleaseAsync(boolean readLockIdempotentReleaseAsync) {
        this.readLockIdempotentReleaseAsync = readLockIdempotentReleaseAsync;
    }

    public int getReadLockIdempotentReleaseAsyncPoolSize() {
        return readLockIdempotentReleaseAsyncPoolSize;
    }

    /**
     * The number of threads in the scheduled thread pool when using
     * asynchronous release tasks. Using a default of 1 core threads should be
     * sufficient in almost all use-cases, only set this to a higher value if
     * either updating the idempotent repository is slow, or there are a lot of
     * files to process. This option is not in-use if you use a shared thread
     * pool by configuring the readLockIdempotentReleaseExecutorService option.
     * <p/>
     * See more details at the readLockIdempotentReleaseDelay option.
     */
    public void setReadLockIdempotentReleaseAsyncPoolSize(int readLockIdempotentReleaseAsyncPoolSize) {
        this.readLockIdempotentReleaseAsyncPoolSize = readLockIdempotentReleaseAsyncPoolSize;
    }

    public ScheduledExecutorService getReadLockIdempotentReleaseExecutorService() {
        return readLockIdempotentReleaseExecutorService;
    }

    /**
     * To use a custom and shared thread pool for asynchronous release tasks.
     * <p/>
     * See more details at the readLockIdempotentReleaseDelay option.
     */
    public void setReadLockIdempotentReleaseExecutorService(ScheduledExecutorService readLockIdempotentReleaseExecutorService) {
        this.readLockIdempotentReleaseExecutorService = readLockIdempotentReleaseExecutorService;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Buffer size in bytes used for writing files (or in case of FTP for
     * downloading and uploading files).
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
     * What to do if a file already exists with the same name. Override, which
     * is the default, replaces the existing file. \n\n - Append - adds content
     * to the existing file.\n - Fail - throws a GenericFileOperationException,
     * indicating that there is already an existing file.\n - Ignore - silently
     * ignores the problem and does not override the existing file, but assumes
     * everything is okay.\n - Move - option requires to use the moveExisting
     * option to be configured as well. The option eagerDeleteTargetFile can be
     * used to control what to do if an moving the file, and there exists
     * already an existing file, otherwise causing the move operation to fail.
     * The Move option will move any existing files, before writing the target
     * file.\n - TryRename is only applicable if tempFileName option is in use.
     * This allows to try renaming the file from the temporary name to the
     * actual name, without doing any exists check. This check may be faster on
     * some file systems and especially FTP servers.
     */
    public void setFileExist(GenericFileExist fileExist) {
        this.fileExist = fileExist;
    }

    public boolean isAutoCreate() {
        return autoCreate;
    }

    /**
     * Automatically create missing directories in the file's pathname. For the
     * file consumer, that means creating the starting directory. For the file
     * producer, it means the directory the files should be written to.
     */
    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    public GenericFileProcessStrategy<T> getProcessStrategy() {
        return processStrategy;
    }

    /**
     * A pluggable org.apache.camel.component.file.GenericFileProcessStrategy
     * allowing you to implement your own readLock option or similar. Can also
     * be used when special conditions must be met before a file can be
     * consumed, such as a special ready file exists. If this option is set then
     * the readLock option does not apply.
     */
    public void setProcessStrategy(GenericFileProcessStrategy<T> processStrategy) {
        this.processStrategy = processStrategy;
    }

    public String getLocalWorkDirectory() {
        return localWorkDirectory;
    }

    /**
     * When consuming, a local work directory can be used to store the remote
     * file content directly in local files, to avoid loading the content into
     * memory. This is beneficial, if you consume a very big remote file and
     * thus can conserve memory.
     */
    public void setLocalWorkDirectory(String localWorkDirectory) {
        this.localWorkDirectory = localWorkDirectory;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * To define a maximum messages to gather per poll. By default no maximum is
     * set. Can be used to set a limit of e.g. 1000 to avoid when starting up
     * the server that there are thousands of files. Set a value of 0 or
     * negative to disabled it. Notice: If this option is in use then the File
     * and FTP components will limit before any sorting. For example if you have
     * 100000 files and use maxMessagesPerPoll=500, then only the first 500
     * files will be picked up, and then sorted. You can use the
     * eagerMaxMessagesPerPoll option and set this to false to allow to scan all
     * files first and then sort afterwards.
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public boolean isEagerMaxMessagesPerPoll() {
        return eagerMaxMessagesPerPoll;
    }

    /**
     * Allows for controlling whether the limit from maxMessagesPerPoll is eager
     * or not. If eager then the limit is during the scanning of files. Where as
     * false would scan all files, and then perform sorting. Setting this option
     * to false allows for sorting all files first, and then limit the poll.
     * Mind that this requires a higher memory usage as all file details are in
     * memory to perform the sorting.
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
     * The minimum depth to start processing when recursively processing a
     * directory. Using minDepth=1 means the base directory. Using minDepth=2
     * means the first sub directory.
     */
    public void setMinDepth(int minDepth) {
        this.minDepth = minDepth;
    }

    public IdempotentRepository getInProgressRepository() {
        return inProgressRepository;
    }

    /**
     * A pluggable in-progress repository
     * org.apache.camel.spi.IdempotentRepository. The in-progress repository is
     * used to account the current in progress files being consumed. By default
     * a memory based repository is used.
     */
    public void setInProgressRepository(IdempotentRepository inProgressRepository) {
        this.inProgressRepository = inProgressRepository;
    }

    public boolean isKeepLastModified() {
        return keepLastModified;
    }

    /**
     * Will keep the last modified timestamp from the source file (if any). Will
     * use the Exchange.FILE_LAST_MODIFIED header to located the timestamp. This
     * header can contain either a java.util.Date or long with the timestamp. If
     * the timestamp exists and the option is enabled it will set this timestamp
     * on the written file. Note: This option only applies to the file producer.
     * You cannot use this option with any of the ftp producers.
     */
    public void setKeepLastModified(boolean keepLastModified) {
        this.keepLastModified = keepLastModified;
    }

    public boolean isAllowNullBody() {
        return allowNullBody;
    }

    /**
     * Used to specify if a null body is allowed during file writing. If set to
     * true then an empty file will be created, when set to false, and
     * attempting to send a null body to the file component, a
     * GenericFileWriteException of 'Cannot write null body to file.' will be
     * thrown. If the `fileExist` option is set to 'Override', then the file
     * will be truncated, and if set to `append` the file will remain unchanged.
     */
    public void setAllowNullBody(boolean allowNullBody) {
        this.allowNullBody = allowNullBody;
    }

    public boolean isJailStartingDirectory() {
        return jailStartingDirectory;
    }

    /**
     * Used for jailing (restricting) writing files to the starting directory
     * (and sub) only. This is enabled by default to not allow Camel to write
     * files to outside directories (to be more secured out of the box). You can
     * turn this off to allow writing files to directories outside the starting
     * directory, such as parent or root folders.
     */
    public void setJailStartingDirectory(boolean jailStartingDirectory) {
        this.jailStartingDirectory = jailStartingDirectory;
    }

    public String getAppendChars() {
        return appendChars;
    }

    /**
     * Used to append characters (text) after writing files. This can for
     * example be used to add new lines or other separators when writing and
     * appending to existing files.
     * <p/>
     * To specify new-line (slash-n or slash-r) or tab (slash-t) characters then
     * escape with an extra slash, eg slash-slash-n
     */
    public void setAppendChars(String appendChars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < appendChars.length(); i++) {
            char ch = appendChars.charAt(i);
            boolean escaped = '\\' == ch;
            if (escaped && i < appendChars.length() - 1) {
                // grab next character to escape
                char next = appendChars.charAt(i + 1);
                // special for new line, tabs and carriage return
                if ('n' == next) {
                    sb.append("\n");
                    i++;
                    continue;
                } else if ('t' == next) {
                    sb.append("\t");
                    i++;
                    continue;
                } else if ('r' == next) {
                    sb.append("\r");
                    i++;
                    continue;
                }
            }
            // not special just a regular character
            sb.append(ch);
        }
        this.appendChars = sb.toString();
    }

    public ExceptionHandler getOnCompletionExceptionHandler() {
        return onCompletionExceptionHandler;
    }

    /**
     * To use a custom {@link org.apache.camel.spi.ExceptionHandler} to handle
     * any thrown exceptions that happens during the file on completion process
     * where the consumer does either a commit or rollback. The default
     * implementation will log any exception at WARN level and ignore.
     */
    public void setOnCompletionExceptionHandler(ExceptionHandler onCompletionExceptionHandler) {
        this.onCompletionExceptionHandler = onCompletionExceptionHandler;
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
            // compute name to set on header that should be relative to starting
            // directory
            String name = file.isAbsolute() ? file.getAbsoluteFilePath() : file.getRelativeFilePath();

            // skip leading endpoint configured directory
            String endpointPath = getConfiguration().getDirectory() + getFileSeparator();

            // need to normalize paths to ensure we can match using startsWith
            endpointPath = FileUtil.normalizePath(endpointPath);
            String copyOfName = FileUtil.normalizePath(name);
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(endpointPath) && copyOfName.startsWith(endpointPath)) {
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
     * Strategy to configure the move, preMove, or moveExisting option based on
     * a String input.
     *
     * @param expression the original string input
     * @return configured string or the original if no modifications is needed
     */
    protected String configureMoveOrPreMoveExpression(String expression) {
        // if the expression already have ${ } placeholders then pass it
        // unmodified
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
        // append only the filename (file:name can contain a relative path, so
        // we must use onlyname)
        sb.append(getFileSeparator());
        sb.append("${file:onlyname}");

        return sb.toString();
    }

    protected Map<String, Object> getParamsAsMap() {
        Map<String, Object> params = new HashMap<>();

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
        if ("idempotent".equals(readLock) || "idempotent-changed".equals(readLock) || "idempotent-rename".equals(readLock)) {
            params.put("readLockIdempotentRepository", idempotentRepository);
        }
        if (readLockCheckInterval > 0) {
            params.put("readLockCheckInterval", readLockCheckInterval);
        }
        if (readLockTimeout > 0) {
            params.put("readLockTimeout", readLockTimeout);
        }
        params.put("readLockMarkerFile", readLockMarkerFile);
        params.put("readLockDeleteOrphanLockFiles", readLockDeleteOrphanLockFiles);
        params.put("readLockMinLength", readLockMinLength);
        params.put("readLockLoggingLevel", readLockLoggingLevel);
        params.put("readLockMinAge", readLockMinAge);
        params.put("readLockRemoveOnRollback", readLockRemoveOnRollback);
        params.put("readLockRemoveOnCommit", readLockRemoveOnCommit);
        if (readLockIdempotentReleaseDelay > 0) {
            params.put("readLockIdempotentReleaseDelay", readLockIdempotentReleaseDelay);
        }
        params.put("readLockIdempotentReleaseAsync", readLockIdempotentReleaseAsync);
        if (readLockIdempotentReleaseAsyncPoolSize > 0) {
            params.put("readLockIdempotentReleaseAsyncPoolSize", readLockIdempotentReleaseAsyncPoolSize);
        }
        if (readLockIdempotentReleaseExecutorService != null) {
            params.put("readLockIdempotentReleaseExecutorService", readLockIdempotentReleaseExecutorService);
        }
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

    private Predicate createFileLanguagePredicate(String expression) {
        Language language = getCamelContext().resolveLanguage("file");
        return language.createPredicate(expression);
    }

    /**
     * Creates the associated name of the done file based on the given file
     * name.
     * <p/>
     * This method should only be invoked if a done filename property has been
     * set on this endpoint.
     *
     * @param fileName the file name
     * @return name of the associated done file name
     */
    protected String createDoneFileName(String fileName) {
        String pattern = getDoneFileName();
        StringHelper.notEmpty(pattern, "doneFileName", pattern);

        // we only support ${file:name} or ${file:name.noext} as dynamic
        // placeholders for done files
        String path = FileUtil.onlyPath(fileName);
        String onlyName = Matcher.quoteReplacement(FileUtil.stripPath(fileName));

        pattern = pattern.replaceFirst("\\$\\{file:name\\}", onlyName);
        pattern = pattern.replaceFirst("\\$simple\\{file:name\\}", onlyName);
        pattern = pattern.replaceFirst("\\$\\{file:name.noext\\}", FileUtil.stripExt(onlyName, true));
        pattern = pattern.replaceFirst("\\$simple\\{file:name.noext\\}", FileUtil.stripExt(onlyName, true));

        // must be able to resolve all placeholders supported
        if (StringHelper.hasStartToken(pattern, "simple")) {
            throw new ExpressionIllegalSyntaxException(fileName + ". Cannot resolve reminder: " + pattern);
        }

        String answer = pattern;
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(path) && org.apache.camel.util.ObjectHelper.isNotEmpty(pattern)) {
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
     * This method should only be invoked if a done filename property has been
     * set on this endpoint.
     *
     * @param fileName the file name
     * @return <tt>true</tt> if its a done file, <tt>false</tt> otherwise
     */
    protected boolean isDoneFile(String fileName) {
        String pattern = getDoneFileName();
        StringHelper.notEmpty(pattern, "doneFileName", pattern);

        if (!StringHelper.hasStartToken(pattern, "simple")) {
            // no tokens, so just match names directly
            return pattern.equals(fileName);
        }

        // the static part of the pattern, is that a prefix or suffix?
        // its a prefix if ${ start token is not at the start of the pattern
        boolean prefix = pattern.indexOf("${") > 0;

        // remove dynamic parts of the pattern so we only got the static part
        // left
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
            if (readLockTimeout > 0 && readLockTimeout <= readLockCheckInterval) {
                throw new IllegalArgumentException("The option readLockTimeout must be higher than readLockCheckInterval" + ", was readLockTimeout=" + readLockTimeout
                                                   + ", readLockCheckInterval=" + readLockCheckInterval
                                                   + ". A good practice is to let the readLockTimeout be at least 3 times higher than the readLockCheckInterval"
                                                   + " to ensure that the read lock procedure has enough time to acquire the lock.");
            }
        }
        if ("idempotent".equals(readLock) && idempotentRepository == null) {
            throw new IllegalArgumentException("IdempotentRepository must be configured when using readLock=idempotent");
        }
        if ("fileLock".equals(readLock) && FileUtil.isWindows()) {
            throw new IllegalArgumentException("The readLock=fileLock option is not supported on Windows");
        }

        if (antInclude != null) {
            if (antFilter == null) {
                antFilter = new AntPathMatcherGenericFileFilter<>();
            }
            antFilter.setIncludes(antInclude);
        }
        if (antExclude != null) {
            if (antFilter == null) {
                antFilter = new AntPathMatcherGenericFileFilter<>();
            }
            antFilter.setExcludes(antExclude);
        }
        if (antFilter != null) {
            antFilter.setCaseSensitive(antFilterCaseSensitive);
        }

        // idempotent repository may be used by others, so add it as a service
        // so its stopped when CamelContext stops
        if (idempotentRepository != null) {
            getCamelContext().addService(idempotentRepository, true);
        }
        ServiceHelper.startService(inProgressRepository);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(inProgressRepository);
    }
}
