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
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

public fun UriDsl.exec(i: ExecUriDsl.() -> Unit) {
  ExecUriDsl(this).apply(i)
}

@CamelDslMarker
public class ExecUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("exec")
  }

  private var executable: String = ""

  public fun executable(executable: String) {
    this.executable = executable
    it.url("$executable")
  }

  public fun args(args: String) {
    it.property("args", args)
  }

  public fun binding(binding: String) {
    it.property("binding", binding)
  }

  public fun commandExecutor(commandExecutor: String) {
    it.property("commandExecutor", commandExecutor)
  }

  public fun commandLogLevel(commandLogLevel: String) {
    it.property("commandLogLevel", commandLogLevel)
  }

  public fun exitValues(exitValues: String) {
    it.property("exitValues", exitValues)
  }

  public fun outFile(outFile: String) {
    it.property("outFile", outFile)
  }

  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  public fun useStderrOnEmptyStdout(useStderrOnEmptyStdout: String) {
    it.property("useStderrOnEmptyStdout", useStderrOnEmptyStdout)
  }

  public fun useStderrOnEmptyStdout(useStderrOnEmptyStdout: Boolean) {
    it.property("useStderrOnEmptyStdout", useStderrOnEmptyStdout.toString())
  }

  public fun workingDir(workingDir: String) {
    it.property("workingDir", workingDir)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
