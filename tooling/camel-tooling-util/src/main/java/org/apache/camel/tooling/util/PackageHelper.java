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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

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
     * Warning, don't use for crazy big streams :)
     */
    public static String loadText(InputStream in) throws IOException {
        return new String(in.readAllBytes());
    }

    public static String loadText(File file) throws IOException {
        return loadText(file.toPath());
    }

    public static String loadText(Path file) throws IOException {
        return loadText(Files.newInputStream(file));
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
     *
     * @param  data the data
     * @return      the map
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

    public static Set<File> findJsonFiles(File rootDir, Set<File> files) {
        return findJsonFiles(rootDir, files, (f) -> true);
    }

    public static Set<File> findJsonFiles(File rootDir, Set<File> files, Predicate<File> filter) {
        try (Stream<Path> stream = findJsonFiles(rootDir.toPath())) {
            stream.map(Path::toFile).filter(filter).forEach(files::add);
        }
        return files;
    }

    public static Stream<Path> findJsonFiles(Path rootDir) {
        return walk(rootDir)
                .filter(p -> p.getFileName().toString().endsWith(JSON_SUFIX));
    }

    public static Stream<Path> walk(Path rootDir) {
        try {
            if (Files.isDirectory(rootDir)) {
                return Files.walk(rootDir);
            } else {
                return Stream.empty();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the name of the component, data format or language from the given json file
     */
    public static String asName(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(JSON_SUFIX)) {
            return name.substring(0, name.length() - JSON_SUFIX.length());
        }
        return name;
    }

    public static File findCamelCoreDirectory(File dir) {
        return findCamelDirectory(dir, "core/camel-core-engine");
    }

    public static File findCamelCoreModelDirectory(File dir) {
        return findCamelDirectory(dir, "core/camel-core-model");
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

    /**
     * Extract the model kind from a given json schema
     */
    public static String getSchemaKind(String json) {
        int i = json.indexOf("\"kind\"");
        if (i >= 0) {
            int s = json.indexOf("\"", i + 6);
            if (s >= 0) {
                int e = json.indexOf("\"", s + 1);
                if (e >= 0) {
                    return json.substring(s + 1, e);
                }
            }
        }
        return null;
    }
}
