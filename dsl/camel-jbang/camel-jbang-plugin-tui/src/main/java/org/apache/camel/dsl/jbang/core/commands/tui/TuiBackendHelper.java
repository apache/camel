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
package org.apache.camel.dsl.jbang.core.commands.tui;

import dev.tamboui.backend.jline3.JLineBackend;
import dev.tamboui.terminal.Backend;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.jline.terminal.Terminal;

final class TuiBackendHelper {

    private TuiBackendHelper() {
    }

    static TuiRunner createTuiRunner() throws Exception {
        Terminal activeTerminal = EnvironmentHelper.getActiveTerminal();
        // Build the JLine backend explicitly rather than leaving backend selection to
        // TamboUI's ServiceLoader-based auto-discovery: with tamboui-aesh-backend also on the
        // classpath (for --web), auto-discovery can pick AeshBackend for the local session too,
        // which drives a native PosixSysTerminal that doesn't shut down cleanly here.
        JLineBackend backend = activeTerminal != null ? new JLineBackend(activeTerminal) : new JLineBackend();
        return TuiRunner.create(TuiConfig.builder().backend(backend).mouseCapture(true).build());
    }

    static TuiRunner createTuiRunner(Backend backend) throws Exception {
        return TuiRunner.create(TuiConfig.builder().backend(backend).mouseCapture(true).build());
    }
}
