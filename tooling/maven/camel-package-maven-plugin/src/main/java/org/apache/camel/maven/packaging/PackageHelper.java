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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.sonatype.plexus.build.incremental.BuildContext;

public final class PackageHelper {

    private PackageHelper() {
    }
    
    public static boolean haveResourcesChanged(Log log, MavenProject project, BuildContext buildContext, String suffix) {
        String baseDir = project.getBasedir().getAbsolutePath();
        for (Resource r : project.getBuild().getResources()) {
            File file = new File(r.getDirectory());
            if (file.isAbsolute()) {
                file = new File(r.getDirectory().substring(baseDir.length() + 1));
            }
            String path = file.getPath() + "/" + suffix;
            if (log.isDebugEnabled()) {
                log.debug("checking  if " + path + " (" + r.getDirectory() + "/" + suffix + ") has changed.");
            }
            if (buildContext.hasDelta(path)) {
                log.debug("Indeed " + suffix + " has changed.");
                return true;
            }
        }
        return false;
    }

    /**
     * Loads the entire stream into memory as a String and returns it.
     * <p/>
     * <b>Notice:</b> This implementation appends a <tt>\n</tt> as line
     * terminator at the of the text.
     * <p/>
     * Warning, don't use for crazy big streams :)
     */
    public static String loadText(InputStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        InputStreamReader isr = new InputStreamReader(in);
        try {
            BufferedReader reader = new LineNumberReader(isr);
            while (true) {
                String line = reader.readLine();
                if (line != null) {
                    builder.append(line);
                    builder.append("\n");
                } else {
                    break;
                }
            }
            return builder.toString();
        } finally {
            isr.close();
            in.close();
        }
    }

    public static void writeText(File file, String text) throws IOException {
        FileOutputStream fos = new FileOutputStream(file, false);
        try {
            fos.write(text.getBytes());
        } finally {
            fos.close();
        }
    }

    public static String after(String text, String after) {
        if (!text.contains(after)) {
            return null;
        }
        return text.substring(text.indexOf(after) + after.length());
    }

    /**
     * Parses the text as a map (eg key=value)
     * @param data the data
     * @return the map
     */
    public static Map<String, String> parseAsMap(String data) {
        Map<String, String> answer = new HashMap<String, String>();
        if (data != null) {
            String[] lines = data.split("\n");
            for (String line : lines) {
                int idx = line.indexOf('=');
                if (idx != -1) {
                    String key = line.substring(0, idx);
                    String value = line.substring(idx + 1);
                    // remove ending line break for the values
                    value = value.trim().replaceAll("\n", "");
                    answer.put(key.trim(), value);
                }
            }
        }
        return answer;
    }

    public static Set<File> findJsonFiles(File dir, FileFilter filter) {
        Set<File> files = new TreeSet<>();
        findJsonFiles(dir, files, filter);

        return files;
    }

    public static void findJsonFiles(File dir, Set<File> found, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                // skip files in root dirs as Camel does not store information there but others may do
                boolean jsonFile = file.isFile() && file.getName().endsWith(".json");
                if (jsonFile) {
                    found.add(file);
                } else if (file.isDirectory()) {
                    findJsonFiles(file, found, filter);
                }
            }
        }
    }

    public static class CamelComponentsModelFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            // skip camel-jetty9 as its a duplicate of camel-jetty
            if ("camel-jetty9".equals(pathname.getName())) {
                return false;
            }
            return pathname.isDirectory() || pathname.getName().endsWith(".json");
        }
    }

    public static class CamelOthersModelFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            String name = pathname.getName();
            boolean special = "camel-core-osgi".equals(name)
                || "camel-core-xml".equals(name)
                || "camel-box".equals(name)
                || "camel-http-common".equals(name)
                || "camel-jetty".equals(name)
                || "camel-jetty-common".equals(name);
            boolean special2 = "camel-linkedin".equals(name)
                || "camel-olingo2".equals(name)
                || "camel-olingo4".equals(name)
                || "camel-salesforce".equals(name);
            if (special || special2) {
                return false;
            }

            return pathname.isDirectory() || name.endsWith(".json");
        }
    }

}
