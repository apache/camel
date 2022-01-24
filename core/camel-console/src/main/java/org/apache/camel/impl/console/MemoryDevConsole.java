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
package org.apache.camel.impl.console;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Map;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.DevConsole;

import static org.apache.camel.util.UnitUtils.printUnitFromBytesDot;

@DevConsole("memory")
@Configurer(bootstrap = true)
public class MemoryDevConsole extends AbstractDevConsole {

    public MemoryDevConsole() {
        super("jvm", "memory", "JVM Memory", "Displays JVM memory information");
    }

    @Override
    protected Object doCall(MediaType mediaType, Map<String, Object> options) {
        // only text is supported
        StringBuilder sb = new StringBuilder();

        MemoryMXBean mb = ManagementFactory.getMemoryMXBean();
        if (mb != null) {
            sb.append(String.format("Heap Init: %s\n", printUnitFromBytesDot(mb.getHeapMemoryUsage().getInit())));
            sb.append(String.format("Heap Max: %s\n", printUnitFromBytesDot(mb.getHeapMemoryUsage().getMax())));
            sb.append(String.format("Heap Used: %s\n", printUnitFromBytesDot(mb.getHeapMemoryUsage().getUsed())));
            sb.append(String.format("Heap Committed: %s\n", printUnitFromBytesDot(mb.getHeapMemoryUsage().getCommitted())));
            sb.append("\n");
            sb.append(String.format("Non-Heap Init: %s\n", printUnitFromBytesDot(mb.getNonHeapMemoryUsage().getInit())));
            sb.append(String.format("Non-Heap Max: %s\n", printUnitFromBytesDot(mb.getNonHeapMemoryUsage().getMax())));
            sb.append(String.format("Non-Heap Used: %s\n", printUnitFromBytesDot(mb.getNonHeapMemoryUsage().getUsed())));
            sb.append(String.format("Non-Heap Committed: %s\n",
                    printUnitFromBytesDot(mb.getNonHeapMemoryUsage().getCommitted())));
        }

        return sb.toString();
    }

}
