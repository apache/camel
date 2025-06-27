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
package org.apache.camel.test.junit5.util;

import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouteCoverageDumperExtension {

    private static final Logger LOG = LoggerFactory.getLogger(RouteCoverageDumperExtension.class);

    private final ModelCamelContext context;

    public RouteCoverageDumperExtension(ModelCamelContext context) {
        this.context = context;
    }

    public void dumpRouteCoverage(Class<?> testClass, String currentTestName, long taken) throws Exception {
        String className = testClass.getSimpleName();
        String dir = "target/camel-route-coverage";
        String name = className + "-" + StringHelper.before(currentTestName, "(") + ".xml";

        CamelRouteCoverageDumper routeCoverageDumper = new CamelRouteCoverageDumper();

        ManagedCamelContext mc
                = context != null ? context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class) : null;
        ManagedCamelContextMBean managedCamelContext = mc != null ? mc.getManagedCamelContext() : null;
        if (managedCamelContext == null) {
            LOG.warn("Cannot dump route coverage to file as JMX is not enabled. "
                     + "Add camel-management JAR as dependency and/or override useJmx() method to enable JMX in the unit test classes.");
        } else {
            routeCoverageDumper.dump(managedCamelContext, context, dir, name, getClass().getName(), currentTestName,
                    taken);
        }
    }
}
