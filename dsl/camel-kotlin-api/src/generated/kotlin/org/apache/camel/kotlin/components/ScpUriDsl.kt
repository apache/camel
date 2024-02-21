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

  public fun host(host: String) {
    this.host = host
    it.url("$host:$port/$directoryName")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$host:$port/$directoryName")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port/$directoryName")
  }

  public fun directoryName(directoryName: String) {
    this.directoryName = directoryName
    it.url("$host:$port/$directoryName")
  }

  public fun chmod(chmod: String) {
    it.property("chmod", chmod)
  }

  public fun disconnect(disconnect: String) {
    it.property("disconnect", disconnect)
  }

  public fun disconnect(disconnect: Boolean) {
    it.property("disconnect", disconnect.toString())
  }

  public fun checksumFileAlgorithm(checksumFileAlgorithm: String) {
    it.property("checksumFileAlgorithm", checksumFileAlgorithm)
  }

  public fun fileName(fileName: String) {
    it.property("fileName", fileName)
  }

  public fun flatten(flatten: String) {
    it.property("flatten", flatten)
  }

  public fun flatten(flatten: Boolean) {
    it.property("flatten", flatten.toString())
  }

  public fun jailStartingDirectory(jailStartingDirectory: String) {
    it.property("jailStartingDirectory", jailStartingDirectory)
  }

  public fun jailStartingDirectory(jailStartingDirectory: Boolean) {
    it.property("jailStartingDirectory", jailStartingDirectory.toString())
  }

  public fun strictHostKeyChecking(strictHostKeyChecking: String) {
    it.property("strictHostKeyChecking", strictHostKeyChecking)
  }

  public fun allowNullBody(allowNullBody: String) {
    it.property("allowNullBody", allowNullBody)
  }

  public fun allowNullBody(allowNullBody: Boolean) {
    it.property("allowNullBody", allowNullBody.toString())
  }

  public fun disconnectOnBatchComplete(disconnectOnBatchComplete: String) {
    it.property("disconnectOnBatchComplete", disconnectOnBatchComplete)
  }

  public fun disconnectOnBatchComplete(disconnectOnBatchComplete: Boolean) {
    it.property("disconnectOnBatchComplete", disconnectOnBatchComplete.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun moveExistingFileStrategy(moveExistingFileStrategy: String) {
    it.property("moveExistingFileStrategy", moveExistingFileStrategy)
  }

  public fun connectTimeout(connectTimeout: String) {
    it.property("connectTimeout", connectTimeout)
  }

  public fun soTimeout(soTimeout: String) {
    it.property("soTimeout", soTimeout)
  }

  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  public fun knownHostsFile(knownHostsFile: String) {
    it.property("knownHostsFile", knownHostsFile)
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun preferredAuthentications(preferredAuthentications: String) {
    it.property("preferredAuthentications", preferredAuthentications)
  }

  public fun privateKeyBytes(privateKeyBytes: String) {
    it.property("privateKeyBytes", privateKeyBytes)
  }

  public fun privateKeyFile(privateKeyFile: String) {
    it.property("privateKeyFile", privateKeyFile)
  }

  public fun privateKeyFilePassphrase(privateKeyFilePassphrase: String) {
    it.property("privateKeyFilePassphrase", privateKeyFilePassphrase)
  }

  public fun username(username: String) {
    it.property("username", username)
  }

  public fun useUserKnownHostsFile(useUserKnownHostsFile: String) {
    it.property("useUserKnownHostsFile", useUserKnownHostsFile)
  }

  public fun useUserKnownHostsFile(useUserKnownHostsFile: Boolean) {
    it.property("useUserKnownHostsFile", useUserKnownHostsFile.toString())
  }

  public fun ciphers(ciphers: String) {
    it.property("ciphers", ciphers)
  }
}
