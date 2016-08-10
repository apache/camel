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

    public static File starterSrcDir(File baseDir) {
        return new File(starterDir(baseDir), "src/main/java");
    }

    public static File starterResourceDir(File baseDir) {
        return new File(starterDir(baseDir), "src/main/resources");
    }

    public static File starterDir(File baseDir) {
        String starterName = baseDir.getName() + STARTER_SUFFIX;

        File allStartersDir = allStartersDir(baseDir);
        File starterDir = new File(allStartersDir, starterName);
        return starterDir;
    }

    public static File allStartersDir(File baseDir) {
        File allStartersDir = new File(camelProjectRoot(baseDir), "components-starter");
        return allStartersDir;
    }

    public static File camelProjectRoot(File baseDir) {
        try {
            File root = baseDir.getCanonicalFile();
            while (root != null && !root.getName().equals("camel")) {
                root = root.getParentFile();
            }

            if (root == null) {
                throw new IllegalStateException("Cannot find project root");
            }
            return root;
        } catch (IOException e) {
            throw new IllegalStateException("Error while getting directory", e);
        }
    }


}
