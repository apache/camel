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

import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.util.Shell;

import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public abstract class HdfsTestSupport extends CamelTestSupport {

    public static final File CWD = new File(".");

    public void checkTest() {
        isJavaFromIbm();
        missingLocalHadoopConfiguration();
        missingAuthenticationConfiguration();
    }

    protected static void isJavaFromIbm() {
        // Hadoop doesn't run on IBM JDK
        assumeFalse(System.getProperty("java.vendor").contains("IBM"), "IBM JDK not supported");
    }

    private static void missingLocalHadoopConfiguration() {
        boolean hasLocalHadoop;
        try {
            String hadoopHome = Shell.getHadoopHome();
            hasLocalHadoop = StringUtils.isNotEmpty(hadoopHome);
        } catch (Exception e) {
            hasLocalHadoop = false;
        }
        assumeTrue(hasLocalHadoop, "Missing local hadoop configuration");
    }

    private void missingAuthenticationConfiguration() {
        try {
            javax.security.auth.login.Configuration.getConfiguration();
        } catch (Exception e) {
            assumeTrue(false, "Missing authentication configuration: " + e);
        }
    }

}
