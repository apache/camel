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
package org.apache.camel.maven;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to find, read json files.
 */
public final class PackageHelper {

    private PackageHelper() {
    }

    public static String fileToString(File file) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(file.toURI()));
        return new String(encoded, Charset.defaultCharset());
    }

    public static Map<String, File> findJsonFiles(File rootDir) {
        Map<String, File> results = new HashMap<String, File>();
        findJsonFiles0(rootDir, results, new CamelComponentsModelFilter());
        return results;
    }

    private static void findJsonFiles0(File dir, Map<String, File> result, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                // skip files in root dirs as Camel does not store information there but others may do
                boolean jsonFile = file.isFile() && file.getName().endsWith(Constants.JSON_SUFIX);
                if (jsonFile) {
                    result.put(file.getName().replaceAll("\\" + Constants.JSON_SUFIX, ""), file);
                } else if (file.isDirectory()) {
                    findJsonFiles0(file, result, filter);
                }
            }
        }
    }

    private static class CamelComponentsModelFilter implements FileFilter {
        @Override
        public boolean accept(File pathname) {
            return pathname.isDirectory() || pathname.getName().endsWith(Constants.JSON_SUFIX);
        }
    }
}
