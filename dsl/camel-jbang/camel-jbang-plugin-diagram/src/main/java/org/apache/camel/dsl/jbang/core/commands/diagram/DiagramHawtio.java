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
package org.apache.camel.dsl.jbang.core.commands.diagram;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.process.Hawtio;
import org.apache.camel.dsl.jbang.core.common.Printer;

/**
 * A Hawtio variant that attaches Jolokia via {@link JolokiaAttacher} (direct VirtualMachine.attach) instead of the
 * standard {@code Jolokia} command. This avoids the "URI is not hierarchical" failure that occurs when the standard
 * Jolokia client tries to locate its own JAR while running inside a fat JAR.
 */
final class DiagramHawtio extends Hawtio {

    private final String targetName;
    private final JolokiaAttacher attacher;
    private long attachedPid;

    DiagramHawtio(CamelJBangMain main, Printer printer, String targetName) {
        super(main);
        this.targetName = targetName;
        this.attacher = new JolokiaAttacher(printer);
    }

    @Override
    protected Integer connectJolokia() throws Exception {
        attachedPid = attacher.resolvePid(targetName);
        if (attachedPid == 0) {
            return 1;
        }
        return attacher.attach(attachedPid, 0);
    }

    @Override
    protected void disconnectJolokia() throws Exception {
        if (attachedPid > 0) {
            attacher.detach(attachedPid);
        }
    }
}
