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
package org.apache.camel.kotlin.components

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * Read and write files.
 */
public fun UriDsl.`file`(i: FileUriDsl.() -> Unit) {
  FileUriDsl(this).apply(i)
}

@CamelDslMarker
public class FileUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("file")
  }

  private var directoryName: String = ""

  /**
   * The starting directory
   */
  public fun directoryName(directoryName: String) {
    this.directoryName = directoryName
    it.url("$directoryName")
  }

  /**
   * This option is used to specify the encoding of the file. You can use this on the consumer, to
   * specify the encodings of the files, which allow Camel to know the charset it should load the file
   * content in case the file content is being accessed. Likewise when writing a file, you can use this
   * option to specify which charset to write the file as well. Do mind that when writing the file
   * Camel may have to read the message content into memory to be able to convert the data into the
   * configured charset, so do not use this if you have big messages.
   */
  public fun charset(charset: String) {
    it.property("charset", charset)
  }

  /**
   * Producer: If provided, then Camel will write a 2nd done file when the original file has been
   * written. The done file will be empty. This option configures what file name to use. Either you can
   * specify a fixed name. Or you can use dynamic placeholders. The done file will always be written in
   * the same folder as the original file. Consumer: If provided, Camel will only consume files if a
   * done file exists. This option configures what file name to use. Either you can specify a fixed
   * name. Or you can use dynamic placeholders.The done file is always expected in the same folder as
   * the original file. Only ${file.name} and ${file.name.next} is supported as dynamic placeholders.
   */
  public fun doneFileName(doneFileName: String) {
    it.property("doneFileName", doneFileName)
  }

  /**
   * Use Expression such as File Language to dynamically set the filename. For consumers, it's used
   * as a filename filter. For producers, it's used to evaluate the filename to write. If an expression
   * is set, it take precedence over the CamelFileName header. (Note: The header itself can also be an
   * Expression). The expression options support both String and Expression types. If the expression is
   * a String type, it is always evaluated using the File Language. If the expression is an Expression
   * type, the specified Expression type is used - this allows you, for instance, to use OGNL
   * expressions. For the consumer, you can use it to filter filenames, so you can for instance consume
   * today's file using the File Language syntax: mydata-${date:now:yyyyMMdd}.txt. The producers
   * support the CamelOverruleFileName header which takes precedence over any existing CamelFileName
   * header; the CamelOverruleFileName is a header that is used only once, and makes it easier as this
   * avoids to temporary store CamelFileName and have to restore it afterwards.
   */
  public fun fileName(fileName: String) {
    it.property("fileName", fileName)
  }

  /**
   * If true, the file will be deleted after it is processed successfully.
   */
  public fun delete(delete: String) {
    it.property("delete", delete)
  }

  /**
   * If true, the file will be deleted after it is processed successfully.
   */
  public fun delete(delete: Boolean) {
    it.property("delete", delete.toString())
  }

  /**
   * Sets the move failure expression based on Simple language. For example, to move files into a
   * .error subdirectory use: .error. Note: When moving the files to the fail location Camel will
   * handle the error and will not pick up the file again.
   */
  public fun moveFailed(moveFailed: String) {
    it.property("moveFailed", moveFailed)
  }

  /**
   * If true, the file is not moved or deleted in any way. This option is good for readonly data, or
   * for ETL type requirements. If noop=true, Camel will set idempotent=true as well, to avoid
   * consuming the same files over and over again.
   */
  public fun noop(noop: String) {
    it.property("noop", noop)
  }

  /**
   * If true, the file is not moved or deleted in any way. This option is good for readonly data, or
   * for ETL type requirements. If noop=true, Camel will set idempotent=true as well, to avoid
   * consuming the same files over and over again.
   */
  public fun noop(noop: Boolean) {
    it.property("noop", noop.toString())
  }

  /**
   * Expression (such as File Language) used to dynamically set the filename when moving it before
   * processing. For example to move in-progress files into the order directory set this value to
   * order.
   */
  public fun preMove(preMove: String) {
    it.property("preMove", preMove)
  }

  /**
   * When pre-sort is enabled then the consumer will sort the file and directory names during
   * polling, that was retrieved from the file system. You may want to do this in case you need to
   * operate on the files in a sorted order. The pre-sort is executed before the consumer starts to
   * filter, and accept files to process by Camel. This option is default=false meaning disabled.
   */
  public fun preSort(preSort: String) {
    it.property("preSort", preSort)
  }

  /**
   * When pre-sort is enabled then the consumer will sort the file and directory names during
   * polling, that was retrieved from the file system. You may want to do this in case you need to
   * operate on the files in a sorted order. The pre-sort is executed before the consumer starts to
   * filter, and accept files to process by Camel. This option is default=false meaning disabled.
   */
  public fun preSort(preSort: Boolean) {
    it.property("preSort", preSort.toString())
  }

  /**
   * If a directory, will look for files in all the sub-directories as well.
   */
  public fun recursive(recursive: String) {
    it.property("recursive", recursive)
  }

  /**
   * If a directory, will look for files in all the sub-directories as well.
   */
  public fun recursive(recursive: Boolean) {
    it.property("recursive", recursive.toString())
  }

  /**
   * If the polling consumer did not poll any files, you can enable this option to send an empty
   * message (no body) instead.
   */
  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  /**
   * If the polling consumer did not poll any files, you can enable this option to send an empty
   * message (no body) instead.
   */
  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  /**
   * Similar to the startingDirectoryMustExist option, but this applies during polling (after
   * starting the consumer).
   */
  public fun directoryMustExist(directoryMustExist: String) {
    it.property("directoryMustExist", directoryMustExist)
  }

  /**
   * Similar to the startingDirectoryMustExist option, but this applies during polling (after
   * starting the consumer).
   */
  public fun directoryMustExist(directoryMustExist: Boolean) {
    it.property("directoryMustExist", directoryMustExist.toString())
  }

  /**
   * To let the consumer use a custom ExceptionHandler. Notice if the option bridgeErrorHandler is
   * enabled then this option is not in use. By default the consumer will deal with exceptions, that
   * will be logged at WARN or ERROR level and ignored.
   */
  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  /**
   * Sets the exchange pattern when the consumer creates an exchange.
   */
  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  /**
   * To define which file attributes of interest. Like
   * posix:permissions,posix:owner,basic:lastAccessTime, it supports basic wildcard like posix:,
   * basic:lastAccessTime
   */
  public fun extendedAttributes(extendedAttributes: String) {
    it.property("extendedAttributes", extendedAttributes)
  }

  /**
   * Whether to accept hidden directories. Directories which names starts with dot are regarded as a
   * hidden directory, and by default are not included. Set this option to true to include hidden
   * directories in the file consumer.
   */
  public fun includeHiddenDirs(includeHiddenDirs: String) {
    it.property("includeHiddenDirs", includeHiddenDirs)
  }

  /**
   * Whether to accept hidden directories. Directories which names starts with dot are regarded as a
   * hidden directory, and by default are not included. Set this option to true to include hidden
   * directories in the file consumer.
   */
  public fun includeHiddenDirs(includeHiddenDirs: Boolean) {
    it.property("includeHiddenDirs", includeHiddenDirs.toString())
  }

  /**
   * Whether to accept hidden files. Files which names starts with dot is regarded as a hidden file,
   * and by default not included. Set this option to true to include hidden files in the file consumer.
   */
  public fun includeHiddenFiles(includeHiddenFiles: String) {
    it.property("includeHiddenFiles", includeHiddenFiles)
  }

  /**
   * Whether to accept hidden files. Files which names starts with dot is regarded as a hidden file,
   * and by default not included. Set this option to true to include hidden files in the file consumer.
   */
  public fun includeHiddenFiles(includeHiddenFiles: Boolean) {
    it.property("includeHiddenFiles", includeHiddenFiles.toString())
  }

  /**
   * A pluggable in-progress repository org.apache.camel.spi.IdempotentRepository. The in-progress
   * repository is used to account the current in progress files being consumed. By default a memory
   * based repository is used.
   */
  public fun inProgressRepository(inProgressRepository: String) {
    it.property("inProgressRepository", inProgressRepository)
  }

  /**
   * When consuming, a local work directory can be used to store the remote file content directly in
   * local files, to avoid loading the content into memory. This is beneficial, if you consume a very
   * big remote file and thus can conserve memory.
   */
  public fun localWorkDirectory(localWorkDirectory: String) {
    it.property("localWorkDirectory", localWorkDirectory)
  }

  /**
   * To use a custom org.apache.camel.spi.ExceptionHandler to handle any thrown exceptions that
   * happens during the file on completion process where the consumer does either a commit or rollback.
   * The default implementation will log any exception at WARN level and ignore.
   */
  public fun onCompletionExceptionHandler(onCompletionExceptionHandler: String) {
    it.property("onCompletionExceptionHandler", onCompletionExceptionHandler)
  }

  /**
   * A pluggable org.apache.camel.PollingConsumerPollingStrategy allowing you to provide your custom
   * implementation to control error handling usually occurred during the poll operation before an
   * Exchange have been created and being routed in Camel.
   */
  public fun pollStrategy(pollStrategy: String) {
    it.property("pollStrategy", pollStrategy)
  }

  /**
   * Whether to enable probing of the content type. If enable then the consumer uses
   * Files#probeContentType(java.nio.file.Path) to determine the content-type of the file, and store
   * that as a header with key Exchange#FILE_CONTENT_TYPE on the Message.
   */
  public fun probeContentType(probeContentType: String) {
    it.property("probeContentType", probeContentType)
  }

  /**
   * Whether to enable probing of the content type. If enable then the consumer uses
   * Files#probeContentType(java.nio.file.Path) to determine the content-type of the file, and store
   * that as a header with key Exchange#FILE_CONTENT_TYPE on the Message.
   */
  public fun probeContentType(probeContentType: Boolean) {
    it.property("probeContentType", probeContentType.toString())
  }

  /**
   * A pluggable org.apache.camel.component.file.GenericFileProcessStrategy allowing you to
   * implement your own readLock option or similar. Can also be used when special conditions must be
   * met before a file can be consumed, such as a special ready file exists. If this option is set then
   * the readLock option does not apply.
   */
  public fun processStrategy(processStrategy: String) {
    it.property("processStrategy", processStrategy)
  }

  /**
   * Whether the starting directory must exist. Mind that the autoCreate option is default enabled,
   * which means the starting directory is normally auto created if it doesn't exist. You can disable
   * autoCreate and enable this to ensure the starting directory must exist. Will throw an exception if
   * the directory doesn't exist.
   */
  public fun startingDirectoryMustExist(startingDirectoryMustExist: String) {
    it.property("startingDirectoryMustExist", startingDirectoryMustExist)
  }

  /**
   * Whether the starting directory must exist. Mind that the autoCreate option is default enabled,
   * which means the starting directory is normally auto created if it doesn't exist. You can disable
   * autoCreate and enable this to ensure the starting directory must exist. Will throw an exception if
   * the directory doesn't exist.
   */
  public fun startingDirectoryMustExist(startingDirectoryMustExist: Boolean) {
    it.property("startingDirectoryMustExist", startingDirectoryMustExist.toString())
  }

  /**
   * Whether the starting directory has access permissions. Mind that the startingDirectoryMustExist
   * parameter must be set to true to verify that the directory exists. Will throw an exception if the
   * directory doesn't have read and write permissions.
   */
  public fun startingDirectoryMustHaveAccess(startingDirectoryMustHaveAccess: String) {
    it.property("startingDirectoryMustHaveAccess", startingDirectoryMustHaveAccess)
  }

  /**
   * Whether the starting directory has access permissions. Mind that the startingDirectoryMustExist
   * parameter must be set to true to verify that the directory exists. Will throw an exception if the
   * directory doesn't have read and write permissions.
   */
  public fun startingDirectoryMustHaveAccess(startingDirectoryMustHaveAccess: Boolean) {
    it.property("startingDirectoryMustHaveAccess", startingDirectoryMustHaveAccess.toString())
  }

  /**
   * Used to append characters (text) after writing files. This can for example be used to add new
   * lines or other separators when writing and appending new files or existing files. To specify
   * new-line (slash-n or slash-r) or tab (slash-t) characters then escape with an extra slash, eg
   * slash-slash-n.
   */
  public fun appendChars(appendChars: String) {
    it.property("appendChars", appendChars)
  }

  /**
   * If provided, then Camel will write a checksum file when the original file has been written. The
   * checksum file will contain the checksum created with the provided algorithm for the original file.
   * The checksum file will always be written in the same folder as the original file.
   */
  public fun checksumFileAlgorithm(checksumFileAlgorithm: String) {
    it.property("checksumFileAlgorithm", checksumFileAlgorithm)
  }

  /**
   * What to do if a file already exists with the same name. Override, which is the default,
   * replaces the existing file. - Append - adds content to the existing file. - Fail - throws a
   * GenericFileOperationException, indicating that there is already an existing file. - Ignore -
   * silently ignores the problem and does not override the existing file, but assumes everything is
   * okay. - Move - option requires to use the moveExisting option to be configured as well. The option
   * eagerDeleteTargetFile can be used to control what to do if an moving the file, and there exists
   * already an existing file, otherwise causing the move operation to fail. The Move option will move
   * any existing files, before writing the target file. - TryRename is only applicable if tempFileName
   * option is in use. This allows to try renaming the file from the temporary name to the actual name,
   * without doing any exists check. This check may be faster on some file systems and especially FTP
   * servers.
   */
  public fun fileExist(fileExist: String) {
    it.property("fileExist", fileExist)
  }

  /**
   * Flatten is used to flatten the file name path to strip any leading paths, so it's just the file
   * name. This allows you to consume recursively into sub-directories, but when you eg write the files
   * to another directory they will be written in a single directory. Setting this to true on the
   * producer enforces that any file name in CamelFileName header will be stripped for any leading
   * paths.
   */
  public fun flatten(flatten: String) {
    it.property("flatten", flatten)
  }

  /**
   * Flatten is used to flatten the file name path to strip any leading paths, so it's just the file
   * name. This allows you to consume recursively into sub-directories, but when you eg write the files
   * to another directory they will be written in a single directory. Setting this to true on the
   * producer enforces that any file name in CamelFileName header will be stripped for any leading
   * paths.
   */
  public fun flatten(flatten: Boolean) {
    it.property("flatten", flatten.toString())
  }

  /**
   * Used for jailing (restricting) writing files to the starting directory (and sub) only. This is
   * enabled by default to not allow Camel to write files to outside directories (to be more secured
   * out of the box). You can turn this off to allow writing files to directories outside the starting
   * directory, such as parent or root folders.
   */
  public fun jailStartingDirectory(jailStartingDirectory: String) {
    it.property("jailStartingDirectory", jailStartingDirectory)
  }

  /**
   * Used for jailing (restricting) writing files to the starting directory (and sub) only. This is
   * enabled by default to not allow Camel to write files to outside directories (to be more secured
   * out of the box). You can turn this off to allow writing files to directories outside the starting
   * directory, such as parent or root folders.
   */
  public fun jailStartingDirectory(jailStartingDirectory: Boolean) {
    it.property("jailStartingDirectory", jailStartingDirectory.toString())
  }

  /**
   * Expression (such as File Language) used to compute file name to use when fileExist=Move is
   * configured. To move files into a backup subdirectory just enter backup. This option only supports
   * the following File Language tokens: file:name, file:name.ext, file:name.noext, file:onlyname,
   * file:onlyname.noext, file:ext, and file:parent. Notice the file:parent is not supported by the FTP
   * component, as the FTP component can only move any existing files to a relative directory based on
   * current dir as base.
   */
  public fun moveExisting(moveExisting: String) {
    it.property("moveExisting", moveExisting)
  }

  /**
   * The same as tempPrefix option but offering a more fine grained control on the naming of the
   * temporary filename as it uses the File Language. The location for tempFilename is relative to the
   * final file location in the option 'fileName', not the target directory in the base uri. For
   * example if option fileName includes a directory prefix: dir/finalFilename then tempFileName is
   * relative to that subdirectory dir.
   */
  public fun tempFileName(tempFileName: String) {
    it.property("tempFileName", tempFileName)
  }

  /**
   * This option is used to write the file using a temporary name and then, after the write is
   * complete, rename it to the real name. Can be used to identify files being written and also avoid
   * consumers (not using exclusive read locks) reading in progress files. Is often used by FTP when
   * uploading big files.
   */
  public fun tempPrefix(tempPrefix: String) {
    it.property("tempPrefix", tempPrefix)
  }

  /**
   * Used to specify if a null body is allowed during file writing. If set to true then an empty
   * file will be created, when set to false, and attempting to send a null body to the file component,
   * a GenericFileWriteException of 'Cannot write null body to file.' will be thrown. If the fileExist
   * option is set to 'Override', then the file will be truncated, and if set to append the file will
   * remain unchanged.
   */
  public fun allowNullBody(allowNullBody: String) {
    it.property("allowNullBody", allowNullBody)
  }

  /**
   * Used to specify if a null body is allowed during file writing. If set to true then an empty
   * file will be created, when set to false, and attempting to send a null body to the file component,
   * a GenericFileWriteException of 'Cannot write null body to file.' will be thrown. If the fileExist
   * option is set to 'Override', then the file will be truncated, and if set to append the file will
   * remain unchanged.
   */
  public fun allowNullBody(allowNullBody: Boolean) {
    it.property("allowNullBody", allowNullBody.toString())
  }

  /**
   * Specify the file permissions that are sent by the producer, the chmod value must be between 000
   * and 777; If there is a leading digit like in 0755, we will ignore it.
   */
  public fun chmod(chmod: String) {
    it.property("chmod", chmod)
  }

  /**
   * Specify the directory permissions used when the producer creates missing directories, the chmod
   * value must be between 000 and 777; If there is a leading digit like in 0755, we will ignore it.
   */
  public fun chmodDirectory(chmodDirectory: String) {
    it.property("chmodDirectory", chmodDirectory)
  }

  /**
   * Whether or not to eagerly delete any existing target file. This option only applies when you
   * use fileExists=Override and the tempFileName option as well. You can use this to disable (set it
   * to false) deleting the target file before the temp file is written. For example you may write big
   * files and want the target file to exists during the temp file is being written. This ensure the
   * target file is only deleted until the very last moment, just before the temp file is being renamed
   * to the target filename. This option is also used to control whether to delete any existing files
   * when fileExist=Move is enabled, and an existing file exists. If this option
   * copyAndDeleteOnRenameFails false, then an exception will be thrown if an existing file existed, if
   * its true, then the existing file is deleted before the move operation.
   */
  public fun eagerDeleteTargetFile(eagerDeleteTargetFile: String) {
    it.property("eagerDeleteTargetFile", eagerDeleteTargetFile)
  }

  /**
   * Whether or not to eagerly delete any existing target file. This option only applies when you
   * use fileExists=Override and the tempFileName option as well. You can use this to disable (set it
   * to false) deleting the target file before the temp file is written. For example you may write big
   * files and want the target file to exists during the temp file is being written. This ensure the
   * target file is only deleted until the very last moment, just before the temp file is being renamed
   * to the target filename. This option is also used to control whether to delete any existing files
   * when fileExist=Move is enabled, and an existing file exists. If this option
   * copyAndDeleteOnRenameFails false, then an exception will be thrown if an existing file existed, if
   * its true, then the existing file is deleted before the move operation.
   */
  public fun eagerDeleteTargetFile(eagerDeleteTargetFile: Boolean) {
    it.property("eagerDeleteTargetFile", eagerDeleteTargetFile.toString())
  }

  /**
   * Whether to force syncing, writes to the file system. You can turn this off if you do not want
   * this level of guarantee, for example, if writing to logs / audit logs etc.; this would yield
   * better performance.
   */
  public fun forceWrites(forceWrites: String) {
    it.property("forceWrites", forceWrites)
  }

  /**
   * Whether to force syncing, writes to the file system. You can turn this off if you do not want
   * this level of guarantee, for example, if writing to logs / audit logs etc.; this would yield
   * better performance.
   */
  public fun forceWrites(forceWrites: Boolean) {
    it.property("forceWrites", forceWrites.toString())
  }

  /**
   * Will keep the last modified timestamp from the source file (if any). Will use the
   * FileConstants.FILE_LAST_MODIFIED header to located the timestamp. This header can contain either a
   * java.util.Date or long with the timestamp. If the timestamp exists and the option is enabled it
   * will set this timestamp on the written file. Note: This option only applies to the file producer.
   * You cannot use this option with any of the ftp producers.
   */
  public fun keepLastModified(keepLastModified: String) {
    it.property("keepLastModified", keepLastModified)
  }

  /**
   * Will keep the last modified timestamp from the source file (if any). Will use the
   * FileConstants.FILE_LAST_MODIFIED header to located the timestamp. This header can contain either a
   * java.util.Date or long with the timestamp. If the timestamp exists and the option is enabled it
   * will set this timestamp on the written file. Note: This option only applies to the file producer.
   * You cannot use this option with any of the ftp producers.
   */
  public fun keepLastModified(keepLastModified: Boolean) {
    it.property("keepLastModified", keepLastModified.toString())
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  /**
   * Strategy (Custom Strategy) used to move file with special naming token to use when
   * fileExist=Move is configured. By default, there is an implementation used if no custom strategy is
   * provided
   */
  public fun moveExistingFileStrategy(moveExistingFileStrategy: String) {
    it.property("moveExistingFileStrategy", moveExistingFileStrategy)
  }

  /**
   * Automatically create missing directories in the file's pathname. For the file consumer, that
   * means creating the starting directory. For the file producer, it means the directory the files
   * should be written to.
   */
  public fun autoCreate(autoCreate: String) {
    it.property("autoCreate", autoCreate)
  }

  /**
   * Automatically create missing directories in the file's pathname. For the file consumer, that
   * means creating the starting directory. For the file producer, it means the directory the files
   * should be written to.
   */
  public fun autoCreate(autoCreate: Boolean) {
    it.property("autoCreate", autoCreate.toString())
  }

  /**
   * Buffer size in bytes used for writing files (or in case of FTP for downloading and uploading
   * files).
   */
  public fun bufferSize(bufferSize: String) {
    it.property("bufferSize", bufferSize)
  }

  /**
   * Buffer size in bytes used for writing files (or in case of FTP for downloading and uploading
   * files).
   */
  public fun bufferSize(bufferSize: Int) {
    it.property("bufferSize", bufferSize.toString())
  }

  /**
   * Whether to fall back and do a copy and delete file, in case the file could not be renamed
   * directly. This option is not available for the FTP component.
   */
  public fun copyAndDeleteOnRenameFail(copyAndDeleteOnRenameFail: String) {
    it.property("copyAndDeleteOnRenameFail", copyAndDeleteOnRenameFail)
  }

  /**
   * Whether to fall back and do a copy and delete file, in case the file could not be renamed
   * directly. This option is not available for the FTP component.
   */
  public fun copyAndDeleteOnRenameFail(copyAndDeleteOnRenameFail: Boolean) {
    it.property("copyAndDeleteOnRenameFail", copyAndDeleteOnRenameFail.toString())
  }

  /**
   * Perform rename operations using a copy and delete strategy. This is primarily used in
   * environments where the regular rename operation is unreliable (e.g., across different file systems
   * or networks). This option takes precedence over the copyAndDeleteOnRenameFail parameter that will
   * automatically fall back to the copy and delete strategy, but only after additional delays.
   */
  public fun renameUsingCopy(renameUsingCopy: String) {
    it.property("renameUsingCopy", renameUsingCopy)
  }

  /**
   * Perform rename operations using a copy and delete strategy. This is primarily used in
   * environments where the regular rename operation is unreliable (e.g., across different file systems
   * or networks). This option takes precedence over the copyAndDeleteOnRenameFail parameter that will
   * automatically fall back to the copy and delete strategy, but only after additional delays.
   */
  public fun renameUsingCopy(renameUsingCopy: Boolean) {
    it.property("renameUsingCopy", renameUsingCopy.toString())
  }

  /**
   * Sets whether synchronous processing should be strictly used
   */
  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  /**
   * Sets whether synchronous processing should be strictly used
   */
  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
  }

  /**
   * Ant style filter exclusion. If both antInclude and antExclude are used, antExclude takes
   * precedence over antInclude. Multiple exclusions may be specified in comma-delimited format.
   */
  public fun antExclude(antExclude: String) {
    it.property("antExclude", antExclude)
  }

  /**
   * Sets case sensitive flag on ant filter.
   */
  public fun antFilterCaseSensitive(antFilterCaseSensitive: String) {
    it.property("antFilterCaseSensitive", antFilterCaseSensitive)
  }

  /**
   * Sets case sensitive flag on ant filter.
   */
  public fun antFilterCaseSensitive(antFilterCaseSensitive: Boolean) {
    it.property("antFilterCaseSensitive", antFilterCaseSensitive.toString())
  }

  /**
   * Ant style filter inclusion. Multiple inclusions may be specified in comma-delimited format.
   */
  public fun antInclude(antInclude: String) {
    it.property("antInclude", antInclude)
  }

  /**
   * Allows for controlling whether the limit from maxMessagesPerPoll is eager or not. If eager then
   * the limit is during the scanning of files. Where as false would scan all files, and then perform
   * sorting. Setting this option to false allows for sorting all files first, and then limit the poll.
   * Mind that this requires a higher memory usage as all file details are in memory to perform the
   * sorting.
   */
  public fun eagerMaxMessagesPerPoll(eagerMaxMessagesPerPoll: String) {
    it.property("eagerMaxMessagesPerPoll", eagerMaxMessagesPerPoll)
  }

  /**
   * Allows for controlling whether the limit from maxMessagesPerPoll is eager or not. If eager then
   * the limit is during the scanning of files. Where as false would scan all files, and then perform
   * sorting. Setting this option to false allows for sorting all files first, and then limit the poll.
   * Mind that this requires a higher memory usage as all file details are in memory to perform the
   * sorting.
   */
  public fun eagerMaxMessagesPerPoll(eagerMaxMessagesPerPoll: Boolean) {
    it.property("eagerMaxMessagesPerPoll", eagerMaxMessagesPerPoll.toString())
  }

  /**
   * Is used to exclude files, if filename matches the regex pattern (matching is case
   * in-sensitive). Notice if you use symbols such as plus sign and others you would need to configure
   * this using the RAW() syntax if configuring this as an endpoint uri. See more details at
   * configuring endpoint uris
   */
  public fun exclude(exclude: String) {
    it.property("exclude", exclude)
  }

  /**
   * Is used to exclude files matching file extension name (case insensitive). For example to
   * exclude bak files, then use excludeExt=bak. Multiple extensions can be separated by comma, for
   * example to exclude bak and dat files, use excludeExt=bak,dat. Note that the file extension
   * includes all parts, for example having a file named mydata.tar.gz will have extension as tar.gz.
   * For more flexibility then use the include/exclude options.
   */
  public fun excludeExt(excludeExt: String) {
    it.property("excludeExt", excludeExt)
  }

  /**
   * Pluggable filter as a org.apache.camel.component.file.GenericFileFilter class. Will skip files
   * if filter returns false in its accept() method.
   */
  public fun filter(filter: String) {
    it.property("filter", filter)
  }

  /**
   * Filters the directory based on Simple language. For example to filter on current date, you can
   * use a simple date pattern such as ${date:now:yyyMMdd}
   */
  public fun filterDirectory(filterDirectory: String) {
    it.property("filterDirectory", filterDirectory)
  }

  /**
   * Filters the file based on Simple language. For example to filter on file size, you can use
   * ${file:size} 5000
   */
  public fun filterFile(filterFile: String) {
    it.property("filterFile", filterFile)
  }

  /**
   * Option to use the Idempotent Consumer EIP pattern to let Camel skip already processed files.
   * Will by default use a memory based LRUCache that holds 1000 entries. If noop=true then idempotent
   * will be enabled as well to avoid consuming the same files over and over again.
   */
  public fun idempotent(idempotent: String) {
    it.property("idempotent", idempotent)
  }

  /**
   * Option to use the Idempotent Consumer EIP pattern to let Camel skip already processed files.
   * Will by default use a memory based LRUCache that holds 1000 entries. If noop=true then idempotent
   * will be enabled as well to avoid consuming the same files over and over again.
   */
  public fun idempotent(idempotent: Boolean) {
    it.property("idempotent", idempotent.toString())
  }

  /**
   * Option to use the Idempotent Consumer EIP pattern to let Camel skip already processed files.
   * Will by default use a memory based LRUCache that holds 1000 entries. If noop=true then idempotent
   * will be enabled as well to avoid consuming the same files over and over again.
   */
  public fun idempotentEager(idempotentEager: String) {
    it.property("idempotentEager", idempotentEager)
  }

  /**
   * Option to use the Idempotent Consumer EIP pattern to let Camel skip already processed files.
   * Will by default use a memory based LRUCache that holds 1000 entries. If noop=true then idempotent
   * will be enabled as well to avoid consuming the same files over and over again.
   */
  public fun idempotentEager(idempotentEager: Boolean) {
    it.property("idempotentEager", idempotentEager.toString())
  }

  /**
   * To use a custom idempotent key. By default the absolute path of the file is used. You can use
   * the File Language, for example to use the file name and file size, you can do:
   * idempotentKey=${file:name}-${file:size}
   */
  public fun idempotentKey(idempotentKey: String) {
    it.property("idempotentKey", idempotentKey)
  }

  /**
   * A pluggable repository org.apache.camel.spi.IdempotentRepository which by default use
   * MemoryIdempotentRepository if none is specified and idempotent is true.
   */
  public fun idempotentRepository(idempotentRepository: String) {
    it.property("idempotentRepository", idempotentRepository)
  }

  /**
   * Is used to include files, if filename matches the regex pattern (matching is case
   * in-sensitive). Notice if you use symbols such as plus sign and others you would need to configure
   * this using the RAW() syntax if configuring this as an endpoint uri. See more details at
   * configuring endpoint uris
   */
  public fun include(include: String) {
    it.property("include", include)
  }

  /**
   * Is used to include files matching file extension name (case insensitive). For example to
   * include txt files, then use includeExt=txt. Multiple extensions can be separated by comma, for
   * example to include txt and xml files, use includeExt=txt,xml. Note that the file extension
   * includes all parts, for example having a file named mydata.tar.gz will have extension as tar.gz.
   * For more flexibility then use the include/exclude options.
   */
  public fun includeExt(includeExt: String) {
    it.property("includeExt", includeExt)
  }

  /**
   * The maximum depth to traverse when recursively processing a directory.
   */
  public fun maxDepth(maxDepth: String) {
    it.property("maxDepth", maxDepth)
  }

  /**
   * The maximum depth to traverse when recursively processing a directory.
   */
  public fun maxDepth(maxDepth: Int) {
    it.property("maxDepth", maxDepth.toString())
  }

  /**
   * To define a maximum messages to gather per poll. By default no maximum is set. Can be used to
   * set a limit of e.g. 1000 to avoid when starting up the server that there are thousands of files.
   * Set a value of 0 or negative to disabled it. Notice: If this option is in use then the File and
   * FTP components will limit before any sorting. For example if you have 100000 files and use
   * maxMessagesPerPoll=500, then only the first 500 files will be picked up, and then sorted. You can
   * use the eagerMaxMessagesPerPoll option and set this to false to allow to scan all files first and
   * then sort afterwards.
   */
  public fun maxMessagesPerPoll(maxMessagesPerPoll: String) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll)
  }

  /**
   * To define a maximum messages to gather per poll. By default no maximum is set. Can be used to
   * set a limit of e.g. 1000 to avoid when starting up the server that there are thousands of files.
   * Set a value of 0 or negative to disabled it. Notice: If this option is in use then the File and
   * FTP components will limit before any sorting. For example if you have 100000 files and use
   * maxMessagesPerPoll=500, then only the first 500 files will be picked up, and then sorted. You can
   * use the eagerMaxMessagesPerPoll option and set this to false to allow to scan all files first and
   * then sort afterwards.
   */
  public fun maxMessagesPerPoll(maxMessagesPerPoll: Int) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll.toString())
  }

  /**
   * The minimum depth to start processing when recursively processing a directory. Using minDepth=1
   * means the base directory. Using minDepth=2 means the first sub directory.
   */
  public fun minDepth(minDepth: String) {
    it.property("minDepth", minDepth)
  }

  /**
   * The minimum depth to start processing when recursively processing a directory. Using minDepth=1
   * means the base directory. Using minDepth=2 means the first sub directory.
   */
  public fun minDepth(minDepth: Int) {
    it.property("minDepth", minDepth.toString())
  }

  /**
   * Expression (such as Simple Language) used to dynamically set the filename when moving it after
   * processing. To move files into a .done subdirectory just enter .done.
   */
  public fun move(move: String) {
    it.property("move", move)
  }

  /**
   * Pluggable read-lock as a org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy
   * implementation.
   */
  public fun exclusiveReadLockStrategy(exclusiveReadLockStrategy: String) {
    it.property("exclusiveReadLockStrategy", exclusiveReadLockStrategy)
  }

  /**
   * Used by consumer, to only poll the files if it has exclusive read-lock on the file (i.e. the
   * file is not in-progress or being written). Camel will wait until the file lock is granted. This
   * option provides the build in strategies: - none - No read lock is in use - markerFile - Camel
   * creates a marker file (fileName.camelLock) and then holds a lock on it. This option is not
   * available for the FTP component - changed - Changed is using file length/modification timestamp to
   * detect whether the file is currently being copied or not. Will at least use 1 sec to determine
   * this, so this option cannot consume files as fast as the others, but can be more reliable as the
   * JDK IO API cannot always determine whether a file is currently being used by another process. The
   * option readLockCheckInterval can be used to set the check frequency. - fileLock - is for using
   * java.nio.channels.FileLock. This option is not avail for Windows OS and the FTP component. This
   * approach should be avoided when accessing a remote file system via a mount/share unless that file
   * system supports distributed file locks. - rename - rename is for using a try to rename the file as
   * a test if we can get exclusive read-lock. - idempotent - (only for file component) idempotent is
   * for using a idempotentRepository as the read-lock. This allows to use read locks that supports
   * clustering if the idempotent repository implementation supports that. - idempotent-changed - (only
   * for file component) idempotent-changed is for using a idempotentRepository and changed as the
   * combined read-lock. This allows to use read locks that supports clustering if the idempotent
   * repository implementation supports that. - idempotent-rename - (only for file component)
   * idempotent-rename is for using a idempotentRepository and rename as the combined read-lock. This
   * allows to use read locks that supports clustering if the idempotent repository implementation
   * supports that.Notice: The various read locks is not all suited to work in clustered mode, where
   * concurrent consumers on different nodes is competing for the same files on a shared file system.
   * The markerFile using a close to atomic operation to create the empty marker file, but its not
   * guaranteed to work in a cluster. The fileLock may work better but then the file system need to
   * support distributed file locks, and so on. Using the idempotent read lock can support clustering
   * if the idempotent repository supports clustering, such as Hazelcast Component or Infinispan.
   */
  public fun readLock(readLock: String) {
    it.property("readLock", readLock)
  }

  /**
   * Interval in millis for the read-lock, if supported by the read lock. This interval is used for
   * sleeping between attempts to acquire the read lock. For example when using the changed read lock,
   * you can set a higher interval period to cater for slow writes. The default of 1 sec. may be too
   * fast if the producer is very slow writing the file. Notice: For FTP the default
   * readLockCheckInterval is 5000. The readLockTimeout value must be higher than
   * readLockCheckInterval, but a rule of thumb is to have a timeout that is at least 2 or more times
   * higher than the readLockCheckInterval. This is needed to ensure that ample time is allowed for the
   * read lock process to try to grab the lock before the timeout was hit.
   */
  public fun readLockCheckInterval(readLockCheckInterval: String) {
    it.property("readLockCheckInterval", readLockCheckInterval)
  }

  /**
   * Interval in millis for the read-lock, if supported by the read lock. This interval is used for
   * sleeping between attempts to acquire the read lock. For example when using the changed read lock,
   * you can set a higher interval period to cater for slow writes. The default of 1 sec. may be too
   * fast if the producer is very slow writing the file. Notice: For FTP the default
   * readLockCheckInterval is 5000. The readLockTimeout value must be higher than
   * readLockCheckInterval, but a rule of thumb is to have a timeout that is at least 2 or more times
   * higher than the readLockCheckInterval. This is needed to ensure that ample time is allowed for the
   * read lock process to try to grab the lock before the timeout was hit.
   */
  public fun readLockCheckInterval(readLockCheckInterval: Int) {
    it.property("readLockCheckInterval", readLockCheckInterval.toString())
  }

  /**
   * Whether or not read lock with marker files should upon startup delete any orphan read lock
   * files, which may have been left on the file system, if Camel was not properly shutdown (such as a
   * JVM crash). If turning this option to false then any orphaned lock file will cause Camel to not
   * attempt to pickup that file, this could also be due another node is concurrently reading files
   * from the same shared directory.
   */
  public fun readLockDeleteOrphanLockFiles(readLockDeleteOrphanLockFiles: String) {
    it.property("readLockDeleteOrphanLockFiles", readLockDeleteOrphanLockFiles)
  }

  /**
   * Whether or not read lock with marker files should upon startup delete any orphan read lock
   * files, which may have been left on the file system, if Camel was not properly shutdown (such as a
   * JVM crash). If turning this option to false then any orphaned lock file will cause Camel to not
   * attempt to pickup that file, this could also be due another node is concurrently reading files
   * from the same shared directory.
   */
  public fun readLockDeleteOrphanLockFiles(readLockDeleteOrphanLockFiles: Boolean) {
    it.property("readLockDeleteOrphanLockFiles", readLockDeleteOrphanLockFiles.toString())
  }

  /**
   * Whether the delayed release task should be synchronous or asynchronous. See more details at the
   * readLockIdempotentReleaseDelay option.
   */
  public fun readLockIdempotentReleaseAsync(readLockIdempotentReleaseAsync: String) {
    it.property("readLockIdempotentReleaseAsync", readLockIdempotentReleaseAsync)
  }

  /**
   * Whether the delayed release task should be synchronous or asynchronous. See more details at the
   * readLockIdempotentReleaseDelay option.
   */
  public fun readLockIdempotentReleaseAsync(readLockIdempotentReleaseAsync: Boolean) {
    it.property("readLockIdempotentReleaseAsync", readLockIdempotentReleaseAsync.toString())
  }

  /**
   * The number of threads in the scheduled thread pool when using asynchronous release tasks. Using
   * a default of 1 core threads should be sufficient in almost all use-cases, only set this to a
   * higher value if either updating the idempotent repository is slow, or there are a lot of files to
   * process. This option is not in-use if you use a shared thread pool by configuring the
   * readLockIdempotentReleaseExecutorService option. See more details at the
   * readLockIdempotentReleaseDelay option.
   */
  public
      fun readLockIdempotentReleaseAsyncPoolSize(readLockIdempotentReleaseAsyncPoolSize: String) {
    it.property("readLockIdempotentReleaseAsyncPoolSize", readLockIdempotentReleaseAsyncPoolSize)
  }

  /**
   * The number of threads in the scheduled thread pool when using asynchronous release tasks. Using
   * a default of 1 core threads should be sufficient in almost all use-cases, only set this to a
   * higher value if either updating the idempotent repository is slow, or there are a lot of files to
   * process. This option is not in-use if you use a shared thread pool by configuring the
   * readLockIdempotentReleaseExecutorService option. See more details at the
   * readLockIdempotentReleaseDelay option.
   */
  public fun readLockIdempotentReleaseAsyncPoolSize(readLockIdempotentReleaseAsyncPoolSize: Int) {
    it.property("readLockIdempotentReleaseAsyncPoolSize",
        readLockIdempotentReleaseAsyncPoolSize.toString())
  }

  /**
   * Whether to delay the release task for a period of millis. This can be used to delay the release
   * tasks to expand the window when a file is regarded as read-locked, in an active/active cluster
   * scenario with a shared idempotent repository, to ensure other nodes cannot potentially scan and
   * acquire the same file, due to race-conditions. By expanding the time-window of the release tasks
   * helps prevents these situations. Note delaying is only needed if you have configured
   * readLockRemoveOnCommit to true.
   */
  public fun readLockIdempotentReleaseDelay(readLockIdempotentReleaseDelay: String) {
    it.property("readLockIdempotentReleaseDelay", readLockIdempotentReleaseDelay)
  }

  /**
   * Whether to delay the release task for a period of millis. This can be used to delay the release
   * tasks to expand the window when a file is regarded as read-locked, in an active/active cluster
   * scenario with a shared idempotent repository, to ensure other nodes cannot potentially scan and
   * acquire the same file, due to race-conditions. By expanding the time-window of the release tasks
   * helps prevents these situations. Note delaying is only needed if you have configured
   * readLockRemoveOnCommit to true.
   */
  public fun readLockIdempotentReleaseDelay(readLockIdempotentReleaseDelay: Int) {
    it.property("readLockIdempotentReleaseDelay", readLockIdempotentReleaseDelay.toString())
  }

  /**
   * To use a custom and shared thread pool for asynchronous release tasks. See more details at the
   * readLockIdempotentReleaseDelay option.
   */
  public
      fun readLockIdempotentReleaseExecutorService(readLockIdempotentReleaseExecutorService: String) {
    it.property("readLockIdempotentReleaseExecutorService",
        readLockIdempotentReleaseExecutorService)
  }

  /**
   * Logging level used when a read lock could not be acquired. By default a DEBUG is logged. You
   * can change this level, for example to OFF to not have any logging. This option is only applicable
   * for readLock of types: changed, fileLock, idempotent, idempotent-changed, idempotent-rename,
   * rename.
   */
  public fun readLockLoggingLevel(readLockLoggingLevel: String) {
    it.property("readLockLoggingLevel", readLockLoggingLevel)
  }

  /**
   * Whether to use marker file with the changed, rename, or exclusive read lock types. By default a
   * marker file is used as well to guard against other processes picking up the same files. This
   * behavior can be turned off by setting this option to false. For example if you do not want to
   * write marker files to the file systems by the Camel application.
   */
  public fun readLockMarkerFile(readLockMarkerFile: String) {
    it.property("readLockMarkerFile", readLockMarkerFile)
  }

  /**
   * Whether to use marker file with the changed, rename, or exclusive read lock types. By default a
   * marker file is used as well to guard against other processes picking up the same files. This
   * behavior can be turned off by setting this option to false. For example if you do not want to
   * write marker files to the file systems by the Camel application.
   */
  public fun readLockMarkerFile(readLockMarkerFile: Boolean) {
    it.property("readLockMarkerFile", readLockMarkerFile.toString())
  }

  /**
   * This option is applied only for readLock=changed. It allows to specify a minimum age the file
   * must be before attempting to acquire the read lock. For example use readLockMinAge=300s to require
   * the file is at last 5 minutes old. This can speedup the changed read lock as it will only attempt
   * to acquire files which are at least that given age.
   */
  public fun readLockMinAge(readLockMinAge: String) {
    it.property("readLockMinAge", readLockMinAge)
  }

  /**
   * This option is applied only for readLock=changed. It allows to specify a minimum age the file
   * must be before attempting to acquire the read lock. For example use readLockMinAge=300s to require
   * the file is at last 5 minutes old. This can speedup the changed read lock as it will only attempt
   * to acquire files which are at least that given age.
   */
  public fun readLockMinAge(readLockMinAge: Int) {
    it.property("readLockMinAge", readLockMinAge.toString())
  }

  /**
   * This option is applied only for readLock=changed. It allows you to configure a minimum file
   * length. By default Camel expects the file to contain data, and thus the default value is 1. You
   * can set this option to zero, to allow consuming zero-length files.
   */
  public fun readLockMinLength(readLockMinLength: String) {
    it.property("readLockMinLength", readLockMinLength)
  }

  /**
   * This option is applied only for readLock=changed. It allows you to configure a minimum file
   * length. By default Camel expects the file to contain data, and thus the default value is 1. You
   * can set this option to zero, to allow consuming zero-length files.
   */
  public fun readLockMinLength(readLockMinLength: Int) {
    it.property("readLockMinLength", readLockMinLength.toString())
  }

  /**
   * This option is applied only for readLock=idempotent. It allows to specify whether to remove the
   * file name entry from the idempotent repository when processing the file is succeeded and a commit
   * happens. By default the file is not removed which ensures that any race-condition do not occur so
   * another active node may attempt to grab the file. Instead the idempotent repository may support
   * eviction strategies that you can configure to evict the file name entry after X minutes - this
   * ensures no problems with race conditions. See more details at the readLockIdempotentReleaseDelay
   * option.
   */
  public fun readLockRemoveOnCommit(readLockRemoveOnCommit: String) {
    it.property("readLockRemoveOnCommit", readLockRemoveOnCommit)
  }

  /**
   * This option is applied only for readLock=idempotent. It allows to specify whether to remove the
   * file name entry from the idempotent repository when processing the file is succeeded and a commit
   * happens. By default the file is not removed which ensures that any race-condition do not occur so
   * another active node may attempt to grab the file. Instead the idempotent repository may support
   * eviction strategies that you can configure to evict the file name entry after X minutes - this
   * ensures no problems with race conditions. See more details at the readLockIdempotentReleaseDelay
   * option.
   */
  public fun readLockRemoveOnCommit(readLockRemoveOnCommit: Boolean) {
    it.property("readLockRemoveOnCommit", readLockRemoveOnCommit.toString())
  }

  /**
   * This option is applied only for readLock=idempotent. It allows to specify whether to remove the
   * file name entry from the idempotent repository when processing the file failed and a rollback
   * happens. If this option is false, then the file name entry is confirmed (as if the file did a
   * commit).
   */
  public fun readLockRemoveOnRollback(readLockRemoveOnRollback: String) {
    it.property("readLockRemoveOnRollback", readLockRemoveOnRollback)
  }

  /**
   * This option is applied only for readLock=idempotent. It allows to specify whether to remove the
   * file name entry from the idempotent repository when processing the file failed and a rollback
   * happens. If this option is false, then the file name entry is confirmed (as if the file did a
   * commit).
   */
  public fun readLockRemoveOnRollback(readLockRemoveOnRollback: Boolean) {
    it.property("readLockRemoveOnRollback", readLockRemoveOnRollback.toString())
  }

  /**
   * Optional timeout in millis for the read-lock, if supported by the read-lock. If the read-lock
   * could not be granted and the timeout triggered, then Camel will skip the file. At next poll Camel,
   * will try the file again, and this time maybe the read-lock could be granted. Use a value of 0 or
   * lower to indicate forever. Currently fileLock, changed and rename support the timeout. Notice: For
   * FTP the default readLockTimeout value is 20000 instead of 10000. The readLockTimeout value must be
   * higher than readLockCheckInterval, but a rule of thumb is to have a timeout that is at least 2 or
   * more times higher than the readLockCheckInterval. This is needed to ensure that ample time is
   * allowed for the read lock process to try to grab the lock before the timeout was hit.
   */
  public fun readLockTimeout(readLockTimeout: String) {
    it.property("readLockTimeout", readLockTimeout)
  }

  /**
   * Optional timeout in millis for the read-lock, if supported by the read-lock. If the read-lock
   * could not be granted and the timeout triggered, then Camel will skip the file. At next poll Camel,
   * will try the file again, and this time maybe the read-lock could be granted. Use a value of 0 or
   * lower to indicate forever. Currently fileLock, changed and rename support the timeout. Notice: For
   * FTP the default readLockTimeout value is 20000 instead of 10000. The readLockTimeout value must be
   * higher than readLockCheckInterval, but a rule of thumb is to have a timeout that is at least 2 or
   * more times higher than the readLockCheckInterval. This is needed to ensure that ample time is
   * allowed for the read lock process to try to grab the lock before the timeout was hit.
   */
  public fun readLockTimeout(readLockTimeout: Int) {
    it.property("readLockTimeout", readLockTimeout.toString())
  }

  /**
   * The number of subsequent error polls (failed due some error) that should happen before the
   * backoffMultipler should kick-in.
   */
  public fun backoffErrorThreshold(backoffErrorThreshold: String) {
    it.property("backoffErrorThreshold", backoffErrorThreshold)
  }

  /**
   * The number of subsequent error polls (failed due some error) that should happen before the
   * backoffMultipler should kick-in.
   */
  public fun backoffErrorThreshold(backoffErrorThreshold: Int) {
    it.property("backoffErrorThreshold", backoffErrorThreshold.toString())
  }

  /**
   * The number of subsequent idle polls that should happen before the backoffMultipler should
   * kick-in.
   */
  public fun backoffIdleThreshold(backoffIdleThreshold: String) {
    it.property("backoffIdleThreshold", backoffIdleThreshold)
  }

  /**
   * The number of subsequent idle polls that should happen before the backoffMultipler should
   * kick-in.
   */
  public fun backoffIdleThreshold(backoffIdleThreshold: Int) {
    it.property("backoffIdleThreshold", backoffIdleThreshold.toString())
  }

  /**
   * To let the scheduled polling consumer backoff if there has been a number of subsequent
   * idles/errors in a row. The multiplier is then the number of polls that will be skipped before the
   * next actual attempt is happening again. When this option is in use then backoffIdleThreshold
   * and/or backoffErrorThreshold must also be configured.
   */
  public fun backoffMultiplier(backoffMultiplier: String) {
    it.property("backoffMultiplier", backoffMultiplier)
  }

  /**
   * To let the scheduled polling consumer backoff if there has been a number of subsequent
   * idles/errors in a row. The multiplier is then the number of polls that will be skipped before the
   * next actual attempt is happening again. When this option is in use then backoffIdleThreshold
   * and/or backoffErrorThreshold must also be configured.
   */
  public fun backoffMultiplier(backoffMultiplier: Int) {
    it.property("backoffMultiplier", backoffMultiplier.toString())
  }

  /**
   * Milliseconds before the next poll.
   */
  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  /**
   * Milliseconds before the next poll.
   */
  public fun delay(delay: Int) {
    it.property("delay", delay.toString())
  }

  /**
   * If greedy is enabled, then the ScheduledPollConsumer will run immediately again, if the
   * previous run polled 1 or more messages.
   */
  public fun greedy(greedy: String) {
    it.property("greedy", greedy)
  }

  /**
   * If greedy is enabled, then the ScheduledPollConsumer will run immediately again, if the
   * previous run polled 1 or more messages.
   */
  public fun greedy(greedy: Boolean) {
    it.property("greedy", greedy.toString())
  }

  /**
   * Milliseconds before the first poll starts.
   */
  public fun initialDelay(initialDelay: String) {
    it.property("initialDelay", initialDelay)
  }

  /**
   * Milliseconds before the first poll starts.
   */
  public fun initialDelay(initialDelay: Int) {
    it.property("initialDelay", initialDelay.toString())
  }

  /**
   * Specifies a maximum limit of number of fires. So if you set it to 1, the scheduler will only
   * fire once. If you set it to 5, it will only fire five times. A value of zero or negative means
   * fire forever.
   */
  public fun repeatCount(repeatCount: String) {
    it.property("repeatCount", repeatCount)
  }

  /**
   * Specifies a maximum limit of number of fires. So if you set it to 1, the scheduler will only
   * fire once. If you set it to 5, it will only fire five times. A value of zero or negative means
   * fire forever.
   */
  public fun repeatCount(repeatCount: Int) {
    it.property("repeatCount", repeatCount.toString())
  }

  /**
   * The consumer logs a start/complete log line when it polls. This option allows you to configure
   * the logging level for that.
   */
  public fun runLoggingLevel(runLoggingLevel: String) {
    it.property("runLoggingLevel", runLoggingLevel)
  }

  /**
   * Allows for configuring a custom/shared thread pool to use for the consumer. By default each
   * consumer has its own single threaded thread pool.
   */
  public fun scheduledExecutorService(scheduledExecutorService: String) {
    it.property("scheduledExecutorService", scheduledExecutorService)
  }

  /**
   * To use a cron scheduler from either camel-spring or camel-quartz component. Use value spring or
   * quartz for built in scheduler
   */
  public fun scheduler(scheduler: String) {
    it.property("scheduler", scheduler)
  }

  /**
   * To configure additional properties when using a custom scheduler or any of the Quartz, Spring
   * based scheduler.
   */
  public fun schedulerProperties(schedulerProperties: String) {
    it.property("schedulerProperties", schedulerProperties)
  }

  /**
   * Whether the scheduler should be auto started.
   */
  public fun startScheduler(startScheduler: String) {
    it.property("startScheduler", startScheduler)
  }

  /**
   * Whether the scheduler should be auto started.
   */
  public fun startScheduler(startScheduler: Boolean) {
    it.property("startScheduler", startScheduler.toString())
  }

  /**
   * Time unit for initialDelay and delay options.
   */
  public fun timeUnit(timeUnit: String) {
    it.property("timeUnit", timeUnit)
  }

  /**
   * Controls if fixed delay or fixed rate is used. See ScheduledExecutorService in JDK for details.
   */
  public fun useFixedDelay(useFixedDelay: String) {
    it.property("useFixedDelay", useFixedDelay)
  }

  /**
   * Controls if fixed delay or fixed rate is used. See ScheduledExecutorService in JDK for details.
   */
  public fun useFixedDelay(useFixedDelay: Boolean) {
    it.property("useFixedDelay", useFixedDelay.toString())
  }

  /**
   * To shuffle the list of files (sort in random order)
   */
  public fun shuffle(shuffle: String) {
    it.property("shuffle", shuffle)
  }

  /**
   * To shuffle the list of files (sort in random order)
   */
  public fun shuffle(shuffle: Boolean) {
    it.property("shuffle", shuffle.toString())
  }

  /**
   * Built-in sort by using the File Language. Supports nested sorts, so you can have a sort by file
   * name and as a 2nd group sort by modified date.
   */
  public fun sortBy(sortBy: String) {
    it.property("sortBy", sortBy)
  }

  /**
   * Pluggable sorter as a java.util.Comparator class.
   */
  public fun sorter(sorter: String) {
    it.property("sorter", sorter)
  }
}
