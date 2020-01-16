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
package org.apache.camel.tooling.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Utility class to find, read json files.
 */
public final class PackageHelper {

    public static final String JSON_SUFIX = ".json";

    private PackageHelper() {
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

    public static String loadText(File file) throws IOException {
        return loadText(new FileInputStream(file));
    }

    public static void writeText(File file, String text) throws IOException {
        FileUtil.updateFile(file.toPath(), text);
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
        Map<String, String> answer = new HashMap<>();
        if (data != null) {
            String[] lines = data.split("\n");
            for (String line : lines) {
                int idx = line.indexOf('=');
                if (idx != -1) {
                    String key = line.substring(0, idx);
                    String value = line.substring(idx + 1);
                    // remove ending line break for the values
                    value = value.trim().replace("\n", "");
                    answer.put(key.trim(), value);
                }
            }
        }
        return answer;
    }

    public static String fileToString(File file) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(file.toURI()));
        return new String(encoded, Charset.defaultCharset());
    }

    public static Map<String, File> findJsonFiles(File rootDir) {
        return findJsonFiles(rootDir, new CamelComponentsModelFilter());
    }

    public static void findJsonFiles(File rootDir, Set<File> files, FileFilter filter) {
        findJsonFiles0(rootDir, files, filter);
    }

    public static Map<String, File> findJsonFiles(File rootDir, FileFilter filter) {
        Set<File> results = new HashSet<>();
        findJsonFiles0(rootDir, results, filter);
        Map<String, File> files = new HashMap<>();
        results.forEach(file -> files.put(file.getName().replace(JSON_SUFIX, ""), file));
        return files;
    }

    private static void findJsonFiles0(File dir, Set<File> result, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                // skip files in root dirs as Camel does not store information there but others may do
                boolean jsonFile = file.isFile() && file.getName().endsWith(JSON_SUFIX);
                if (jsonFile) {
                    result.add(file);
                } else if (file.isDirectory()) {
                    findJsonFiles0(file, result, filter);
                }
            }
        }
    }

    public static class CamelComponentsModelFilter implements FileFilter {
        @Override
        public boolean accept(File pathname) {
            return pathname.isDirectory() || pathname.getName().endsWith(JSON_SUFIX);
        }
    }

    public static class CamelOthersModelFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            String name = pathname.getName();
            boolean special = "camel-core-osgi".equals(name)
                    || "camel-core-xml".equals(name)
                    || "camel-http-base".equals(name)
                    || "camel-http-common".equals(name)
                    || "camel-jetty-common".equals(name);
            boolean special2 = "camel-as2".equals(name)
                    || "camel-box".equals(name)
                    || "camel-olingo2".equals(name)
                    || "camel-olingo4".equals(name)
                    || "camel-salesforce".equals(name)
                    || "camel-debezium-common".equals(name);
            if (special || special2) {
                return false;
            }

            return pathname.isDirectory() || name.endsWith(".json");
        }
    }

    public static File findCamelCoreDirectory(File dir) {
        return findCamelDirectory(dir, "core/camel-core-engine");
    }

    public static File findCamelDirectory(File dir, String path) {
        if (dir == null) {
            return null;
        }
        Path p = dir.toPath().resolve(path);
        if (Files.isDirectory(p)) {
            return p.toFile();
        } else {
            // okay walk up the parent dir
            return findCamelDirectory(dir.getParentFile(), path);
        }
    }


}
