/**
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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.IOException;

/**
 * Useful methods for spring-boot mojos.
 */
public final class SpringBootHelper {

    public static final String STARTER_SUFFIX = "-starter";

    private SpringBootHelper() {
    }

    public static File starterSrcDir(File baseDir, String artifactId) {
        return new File(starterDir(baseDir, artifactId), "src/main/java");
    }

    public static File starterResourceDir(File baseDir, String artifactId) {
        return new File(starterDir(baseDir, artifactId), "src/main/resources");
    }

    public static File starterDir(File baseDir, String artifactId) {
        String starterName = artifactId + STARTER_SUFFIX;

        File allStartersDir = allStartersDir(baseDir);
        File starterDir = new File(allStartersDir, starterName);
        return starterDir;
    }

    public static File allStartersDir(File baseDir) {
        File allStartersDir = new File(camelProjectRoot(baseDir, "platforms"), "platforms/spring-boot/components-starter");
        return allStartersDir;
    }

    public static File camelProjectRoot(File baseDir, String expectedDirName) {
        // another solution could be to look for pom.xml file and see if that pom.xml is the camel root pom
        // however looking for a dir named components-starter should be fine also (there is only 1 with such name)
        try {
            File root = baseDir.getCanonicalFile();
            while (root != null) {
                File[] names = root.listFiles(pathname -> pathname.getName().equals(expectedDirName));
                if (names != null && names.length == 1) {
                    break;
                }
                root = root.getParentFile();
            }

            if (root == null) {
                throw new IllegalStateException("Cannot find Apache Camel project root directory");
            }
            return root;
        } catch (IOException e) {
            throw new IllegalStateException("Error while getting directory", e);
        }
    }
}
