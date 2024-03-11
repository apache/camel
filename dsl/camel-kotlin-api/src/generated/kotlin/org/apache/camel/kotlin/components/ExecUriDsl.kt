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

/**
 * Execute commands on the underlying operating system.
 */
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

  /**
   * Sets the executable to be executed. The executable must not be empty or null.
   */
  public fun executable(executable: String) {
    this.executable = executable
    it.url("$executable")
  }

  /**
   * The arguments may be one or many whitespace-separated tokens.
   */
  public fun args(args: String) {
    it.property("args", args)
  }

  /**
   * A reference to a org.apache.commons.exec.ExecBinding in the Registry.
   */
  public fun binding(binding: String) {
    it.property("binding", binding)
  }

  /**
   * A reference to a org.apache.commons.exec.ExecCommandExecutor in the Registry that customizes
   * the command execution. The default command executor utilizes the commons-exec library, which adds
   * a shutdown hook for every executed command.
   */
  public fun commandExecutor(commandExecutor: String) {
    it.property("commandExecutor", commandExecutor)
  }

  /**
   * Logging level to be used for commands during execution. The default value is DEBUG. Possible
   * values are TRACE, DEBUG, INFO, WARN, ERROR or OFF. (Values of ExecCommandLogLevelType enum)
   */
  public fun commandLogLevel(commandLogLevel: String) {
    it.property("commandLogLevel", commandLogLevel)
  }

  /**
   * The exit values of successful executions. If the process exits with another value, an exception
   * is raised. Comma-separated list of exit values. And empty list (the default) sets no expected exit
   * values and disables the check.
   */
  public fun exitValues(exitValues: String) {
    it.property("exitValues", exitValues)
  }

  /**
   * The name of a file, created by the executable, that should be considered as its output. If no
   * outFile is set, the standard output (stdout) of the executable will be used instead.
   */
  public fun outFile(outFile: String) {
    it.property("outFile", outFile)
  }

  /**
   * The timeout, in milliseconds, after which the executable should be terminated. If execution has
   * not completed within the timeout, the component will send a termination request.
   */
  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  /**
   * A boolean indicating that when stdout is empty, this component will populate the Camel Message
   * Body with stderr. This behavior is disabled (false) by default.
   */
  public fun useStderrOnEmptyStdout(useStderrOnEmptyStdout: String) {
    it.property("useStderrOnEmptyStdout", useStderrOnEmptyStdout)
  }

  /**
   * A boolean indicating that when stdout is empty, this component will populate the Camel Message
   * Body with stderr. This behavior is disabled (false) by default.
   */
  public fun useStderrOnEmptyStdout(useStderrOnEmptyStdout: Boolean) {
    it.property("useStderrOnEmptyStdout", useStderrOnEmptyStdout.toString())
  }

  /**
   * The directory in which the command should be executed. If null, the working directory of the
   * current process will be used.
   */
  public fun workingDir(workingDir: String) {
    it.property("workingDir", workingDir)
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
}
