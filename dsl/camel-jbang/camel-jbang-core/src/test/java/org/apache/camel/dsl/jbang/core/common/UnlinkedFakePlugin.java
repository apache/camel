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
package org.apache.camel.dsl.jbang.core.common;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;

/**
 * A plugin that does not link with the CLI that loads it, such as one cached for another build of the CLI: creating it
 * fails as a class it needs is missing.
 */
public class UnlinkedFakePlugin implements Plugin {

    static {
        // as the JVM fails when the class links: the error, not wrapped
        if (Boolean.TRUE) {
            throw new NoClassDefFoundError("picocli/CommandLine$IExecutionStrategy");
        }
    }

    @Override
    public void customize(CommandTree commandLine, CamelJBangMain main) {
        // no-op
    }
}
