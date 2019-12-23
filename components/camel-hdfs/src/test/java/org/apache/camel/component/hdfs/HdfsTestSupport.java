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
package org.apache.camel.component.hdfs;

import java.io.File;
import java.util.Objects;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.util.Shell;

public abstract class HdfsTestSupport extends CamelTestSupport {

    public static final File CWD = new File(".");

    private static Boolean skipTests;

    public boolean skipTest() {
        if (Objects.isNull(skipTests)) {
            skipTests = notConfiguredToRunTests();
        }

        return skipTests;
    }

    private boolean notConfiguredToRunTests() {
        return isJavaFromIbm() || missingLocalHadoopConfiguration() || missingAuthenticationConfiguration();
    }

    private static boolean isJavaFromIbm() {
        // Hadoop doesn't run on IBM JDK
        return System.getProperty("java.vendor").contains("IBM");
    }

    private static boolean missingLocalHadoopConfiguration() {
        boolean hasLocalHadoop;
        try {
            String hadoopHome = Shell.getHadoopHome();
            hasLocalHadoop = StringUtils.isNotEmpty(hadoopHome);
        } catch (Throwable e) {
            hasLocalHadoop = false;
        }
        return !hasLocalHadoop;
    }

    private boolean missingAuthenticationConfiguration() {
        try {
            javax.security.auth.login.Configuration.getConfiguration();
            return false;
        } catch (Exception e) {
            log.debug("Cannot run test due security exception", e);
            return true;
        }
    }

}
