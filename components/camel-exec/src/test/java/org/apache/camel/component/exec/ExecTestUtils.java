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
package org.apache.camel.component.exec;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public final class ExecTestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ExecTestUtils.class);

    private ExecTestUtils() {
    }

    /**
     * Where on the file system is located the <code>classpathResource</code>?
     * 
     * @param classpathResource a resource in the classpath
     * @return null if the resource does not exist in the classpath. If the file
     *         is not null the resource is guaranteed to exist on the file
     *         system
     */
    public static File getClasspathResourceFileOrNull(String classpathResource) {
        if (classpathResource == null) {
            return null;
        }
        try {
            Resource resource = new ClassPathResource(classpathResource);
            File resourceFile = resource.getFile();
            return resourceFile;
        } catch (IOException ioe) {
            LOG.warn("The resource  " + classpathResource + " does not exist!", ioe);
            return null;
        }
    }

    /**
     * @return the java executable in a system independent way.
     */
    public static String buildJavaExecutablePath() {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
            throw new IllegalStateException("The Exec component tests will fail, because the environment variable JAVA_HOME is not set!");
        }
        File java = new File(javaHome + File.separator + "bin" + File.separator + "java");
        return java.getAbsolutePath();
    }

}
