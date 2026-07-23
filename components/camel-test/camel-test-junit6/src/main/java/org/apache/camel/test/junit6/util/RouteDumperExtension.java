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
package org.apache.camel.test.junit6.util;

import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.spi.DumpRoutesStrategy;
import org.apache.camel.util.StringHelper;

public class RouteDumperExtension {

    private final ModelCamelContext context;

    public RouteDumperExtension(ModelCamelContext context) {
        this.context = context;
    }

    public void dumpRoute(Class<?> testClass, String currentTestName, String format) throws Exception {
        if ("png".equals(format)) {
            // png is route diagrams
            dumpRouteDiagram();
        } else {
            // anything else is regular route dump as text
            String className = testClass.getSimpleName();
            String dir = CamelContextTestHelper.getRouteDumpDir();
            if (dir == null) {
                dir = "target/camel-route-dump";
            }
            String name = className + "-" + StringHelper.before(currentTestName, "(") + "." + format;

            DumpRoutesStrategy drs = context.getCamelContextExtension().getContextPlugin(DumpRoutesStrategy.class);
            drs.setOutput(dir + "/" + name);
            drs.setInclude("*");
            drs.setLog(false);
            drs.setUriAsParameters(true);
            drs.dumpRoutes(format);
        }
    }

    private void dumpRouteDiagram() {
        String dir = CamelContextTestHelper.getRouteDumpDir();
        if (dir == null) {
            dir = "target/camel-route-diagram";
        }
        DumpRoutesStrategy drs = context.getCamelContextExtension().getContextPlugin(DumpRoutesStrategy.class);
        drs.setOutput(dir);
        drs.setInclude("*");
        drs.setLog(false);
        drs.dumpRoutes("png");
    }
}
