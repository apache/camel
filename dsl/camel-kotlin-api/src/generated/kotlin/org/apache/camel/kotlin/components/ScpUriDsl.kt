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
 * Copy files to/from remote hosts using the secure copy protocol (SCP).
 */
public fun UriDsl.scp(i: ScpUriDsl.() -> Unit) {
  ScpUriDsl(this).apply(i)
}

@CamelDslMarker
public class ScpUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("scp")
  }

  private var host: String = ""

  private var port: String = ""

  private var directoryName: String = ""

  /**
   * Hostname of the FTP server
   */
  public fun host(host: String) {
    this.host = host
    it.url("$host:$port/$directoryName")
  }

  /**
   * Port of the FTP server
   */
  public fun port(port: String) {
    this.port = port
    it.url("$host:$port/$directoryName")
  }

  /**
   * Port of the FTP server
   */
  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port/$directoryName")
  }

  /**
   * The starting directory
   */
  public fun directoryName(directoryName: String) {
    this.directoryName = directoryName
    it.url("$host:$port/$directoryName")
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
   * Allows you to set chmod on the stored file. For example chmod=664.
   */
  public fun chmod(chmod: String) {
    it.property("chmod", chmod)
  }

  /**
   * Whether or not to disconnect from remote FTP server right after use. Disconnect will only
   * disconnect the current connection to the FTP server. If you have a consumer which you want to
   * stop, then you need to stop the consumer/route instead.
   */
  public fun disconnect(disconnect: String) {
    it.property("disconnect", disconnect)
  }

  /**
   * Whether or not to disconnect from remote FTP server right after use. Disconnect will only
   * disconnect the current connection to the FTP server. If you have a consumer which you want to
   * stop, then you need to stop the consumer/route instead.
   */
  public fun disconnect(disconnect: Boolean) {
    it.property("disconnect", disconnect.toString())
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
   * Sets whether to use strict host key checking. Possible values are: no, yes
   */
  public fun strictHostKeyChecking(strictHostKeyChecking: String) {
    it.property("strictHostKeyChecking", strictHostKeyChecking)
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
   * Whether or not to disconnect from remote FTP server right after a Batch upload is complete.
   * disconnectOnBatchComplete will only disconnect the current connection to the FTP server.
   */
  public fun disconnectOnBatchComplete(disconnectOnBatchComplete: String) {
    it.property("disconnectOnBatchComplete", disconnectOnBatchComplete)
  }

  /**
   * Whether or not to disconnect from remote FTP server right after a Batch upload is complete.
   * disconnectOnBatchComplete will only disconnect the current connection to the FTP server.
   */
  public fun disconnectOnBatchComplete(disconnectOnBatchComplete: Boolean) {
    it.property("disconnectOnBatchComplete", disconnectOnBatchComplete.toString())
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
   * Maximum number of messages to keep in memory available for browsing. Use 0 for unlimited.
   */
  public fun browseLimit(browseLimit: String) {
    it.property("browseLimit", browseLimit)
  }

  /**
   * Maximum number of messages to keep in memory available for browsing. Use 0 for unlimited.
   */
  public fun browseLimit(browseLimit: Int) {
    it.property("browseLimit", browseLimit.toString())
  }

  /**
   * Sets the connect timeout for waiting for a connection to be established Used by both FTPClient
   * and JSCH
   */
  public fun connectTimeout(connectTimeout: String) {
    it.property("connectTimeout", connectTimeout)
  }

  /**
   * Sets the so timeout FTP and FTPS Is the SocketOptions.SO_TIMEOUT value in millis. Recommended
   * option is to set this to 300000 so as not have a hanged connection. On SFTP this option is set as
   * timeout on the JSCH Session instance.
   */
  public fun soTimeout(soTimeout: String) {
    it.property("soTimeout", soTimeout)
  }

  /**
   * Sets the data timeout for waiting for reply Used only by FTPClient
   */
  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  /**
   * Sets the known_hosts file, so that the jsch endpoint can do host key verification. You can
   * prefix with classpath: to load the file from classpath instead of file system.
   */
  public fun knownHostsFile(knownHostsFile: String) {
    it.property("knownHostsFile", knownHostsFile)
  }

  /**
   * Password to use for login
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Set a comma separated list of authentications that will be used in order of preference.
   * Possible authentication methods are defined by JCraft JSCH. Some examples include:
   * gssapi-with-mic,publickey,keyboard-interactive,password If not specified the JSCH and/or system
   * defaults will be used.
   */
  public fun preferredAuthentications(preferredAuthentications: String) {
    it.property("preferredAuthentications", preferredAuthentications)
  }

  /**
   * Set the private key bytes to that the endpoint can do private key verification. This must be
   * used only if privateKeyFile wasn't set. Otherwise the file will have the priority.
   */
  public fun privateKeyBytes(privateKeyBytes: String) {
    it.property("privateKeyBytes", privateKeyBytes)
  }

  /**
   * Set the private key file to that the endpoint can do private key verification. You can prefix
   * with classpath: to load the file from classpath instead of file system.
   */
  public fun privateKeyFile(privateKeyFile: String) {
    it.property("privateKeyFile", privateKeyFile)
  }

  /**
   * Set the private key file passphrase to that the endpoint can do private key verification.
   */
  public fun privateKeyFilePassphrase(privateKeyFilePassphrase: String) {
    it.property("privateKeyFilePassphrase", privateKeyFilePassphrase)
  }

  /**
   * Username to use for login
   */
  public fun username(username: String) {
    it.property("username", username)
  }

  /**
   * If knownHostFile has not been explicit configured, then use the host file from
   * System.getProperty(user.home) /.ssh/known_hosts
   */
  public fun useUserKnownHostsFile(useUserKnownHostsFile: String) {
    it.property("useUserKnownHostsFile", useUserKnownHostsFile)
  }

  /**
   * If knownHostFile has not been explicit configured, then use the host file from
   * System.getProperty(user.home) /.ssh/known_hosts
   */
  public fun useUserKnownHostsFile(useUserKnownHostsFile: Boolean) {
    it.property("useUserKnownHostsFile", useUserKnownHostsFile.toString())
  }

  /**
   * Set a comma separated list of ciphers that will be used in order of preference. Possible cipher
   * names are defined by JCraft JSCH. Some examples include:
   * aes128-ctr,aes128-cbc,3des-ctr,3des-cbc,blowfish-cbc,aes192-cbc,aes256-cbc. If not specified the
   * default list from JSCH will be used.
   */
  public fun ciphers(ciphers: String) {
    it.property("ciphers", ciphers)
  }
}
