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
package org.apache.camel.test.spring;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper to dump route coverage when using {@link EnableRouteCoverage}.
 */
public final class RouteCoverageDumper {

    private static final Logger LOG = LoggerFactory.getLogger(RouteCoverageDumper.class);

    private RouteCoverageDumper() {
    }

    public static void dumpRouteCoverage(CamelContext context, String testClassName, String testName) {
        try {
            String dir = "target/camel-route-coverage";
            String name = testClassName + "-" + testName + ".xml";

            ManagedCamelContextMBean managedCamelContext = context.getExtension(ManagedCamelContext.class).getManagedCamelContext();
            if (managedCamelContext == null) {
                LOG.warn("Cannot dump route coverage to file as JMX is not enabled. Override useJmx() method to enable JMX in the unit test classes.");
            } else {
                String xml = managedCamelContext.dumpRoutesCoverageAsXml();
                String combined = "<camelRouteCoverage>\n" + gatherTestDetailsAsXml(testClassName, testName) + xml + "\n</camelRouteCoverage>";

                File file = new File(dir);
                // ensure dir exists
                file.mkdirs();
                file = new File(dir, name);

                LOG.info("Dumping route coverage to file: " + file);
                InputStream is = new ByteArrayInputStream(combined.getBytes());
                OutputStream os = new FileOutputStream(file, false);
                IOHelper.copyAndCloseInput(is, os);
                IOHelper.close(os);
            }
        } catch (Exception e) {
            LOG.warn("Error during dumping route coverage statistic. This exception is ignored.", e);
        }

    }

    /**
     * Gathers test details as xml
     */
    private static String gatherTestDetailsAsXml(String testClassName, String testName) {
        StringBuilder sb = new StringBuilder();
        sb.append("<test>\n");
        sb.append("  <class>").append(testClassName).append("</class>\n");
        sb.append("  <method>").append(testName).append("</method>\n");
        sb.append("</test>\n");
        return sb.toString();
    }
}
