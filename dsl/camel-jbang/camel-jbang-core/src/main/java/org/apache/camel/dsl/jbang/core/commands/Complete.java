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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.PrintStream;

import picocli.AutoComplete;
import picocli.CommandLine;

@CommandLine.Command(name = "complete", description = "Generate completion script for bash/zsh")
class Complete extends CamelCommand {

    public Complete(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        String script = AutoComplete.bash(
                spec.parent().name(),
                spec.parent().commandLine());

        // not PrintWriter.println: scripts with Windows line separators fail in strange
        // ways!
        PrintStream out = System.out;
        out.print(script);
        out.print('\n');
        out.flush();
        return 0;
    }

}
