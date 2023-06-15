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
package org.apache.camel.maven;

import java.io.File;
import java.util.Set;

import org.apache.camel.support.PatternHelper;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;

final class ReportPluginCommon {

    private ReportPluginCommon() {
    }

    public static String asRelativeFile(String name, MavenProject project) {
        String answer = name;

        String base = project.getBasedir().getAbsolutePath();
        if (name.startsWith(base)) {
            answer = name.substring(base.length());
            // skip leading slash for relative path
            if (answer.startsWith(File.separator)) {
                answer = answer.substring(1);
            }
        }
        return answer;
    }

    public static String stripRootPath(String name, MavenProject project) {
        // strip out any leading source / resource directory

        for (String dir : project.getCompileSourceRoots()) {
            dir = asRelativeFile(dir, project);
            if (name.startsWith(dir)) {
                return name.substring(dir.length() + 1);
            }
        }
        for (String dir : project.getTestCompileSourceRoots()) {
            dir = asRelativeFile(dir, project);
            if (name.startsWith(dir)) {
                return name.substring(dir.length() + 1);
            }
        }
        for (Resource resource : project.getResources()) {
            String dir = asRelativeFile(resource.getDirectory(), project);
            if (name.startsWith(dir)) {
                return name.substring(dir.length() + 1);
            }
        }
        for (Resource resource : project.getTestResources()) {
            String dir = asRelativeFile(resource.getDirectory(), project);
            if (name.startsWith(dir)) {
                return name.substring(dir.length() + 1);
            }
        }

        return name;
    }

    public static boolean fileListMatchesPattern(String fileList, File file, MavenProject project) {
        for (String exclude : fileList.split(",")) {
            exclude = exclude.trim();
            // try both with and without directory in the name
            String fqn = stripRootPath(asRelativeFile(file.getAbsolutePath(), project), project);
            boolean match = PatternHelper.matchPattern(fqn, exclude) || PatternHelper.matchPattern(file.getName(), exclude);
            if (match) {
                return true;
            }
        }
        return false;
    }

    public static void findXmlFiles(File dir, Set<File> xmlFiles) {
        File[] files = dir.isDirectory() ? dir.listFiles() : null;
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".xml")) {
                    xmlFiles.add(file);
                } else if (file.isDirectory()) {
                    findXmlFiles(file, xmlFiles);
                }
            }
        }
    }

    public static void findJavaFiles(File dir, Set<File> javaFiles) {
        File[] files = dir.isDirectory() ? dir.listFiles() : null;
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                } else if (file.isDirectory()) {
                    findJavaFiles(file, javaFiles);
                }
            }
        }
    }

    public static boolean matchRouteFile(File file, String excludes, String includes, MavenProject project) {
        if (excludes == null && includes == null) {
            return true;
        }

        // exclude take precedence
        if (excludes != null) {
            if (fileListMatchesPattern(excludes, file, project)) {
                return false;
            }
        }

        // include
        if (includes != null) {
            return fileListMatchesPattern(includes, file, project);
        }

        // was not excluded nor failed include so its accepted
        return true;
    }

    public static void findJavaRouteBuilderClasses(
            Set<File> javaFiles, boolean includeJava, boolean includeTest, MavenProject project) {
        if (includeJava) {
            for (String dir : project.getCompileSourceRoots()) {
                findJavaFiles(new File(dir), javaFiles);
            }
            if (includeTest) {
                for (String dir : project.getTestCompileSourceRoots()) {
                    findJavaFiles(new File(dir), javaFiles);
                }
            }
        }
    }

    public static void findXmlRouters(Set<File> xmlFiles, boolean includeXml, boolean includeTest, MavenProject project) {
        if (includeXml) {
            for (Resource dir : project.getResources()) {
                findXmlFiles(new File(dir.getDirectory()), xmlFiles);
            }
            if (includeTest) {
                for (Resource dir : project.getTestResources()) {
                    findXmlFiles(new File(dir.getDirectory()), xmlFiles);
                }
            }
        }
    }
}
