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
package org.apache.camel.util;

/**
 * A resolver for file paths that supports resolving with system and environment properties.
 */
public final class FilePathResolver {

    private FilePathResolver() {
    }

    /**
     * Resolves the path.
     * <p/>
     * The pattern is:
     * <ul>
     * <li><tt>${env:key}</tt> or <tt>${env.key}</tt> for environment variables.</li>
     * <li><tt>${key}</tt> for JVM system properties.</li>
     * </ul>
     * For example: <tt>${env.KARAF_HOME}/data/logs</tt>
     *
     * @param  path                     the path
     * @return                          the resolved path
     * @throws IllegalArgumentException is thrown if system property / environment not found
     */
    public static String resolvePath(String path) throws IllegalArgumentException {
        int count = StringHelper.countChar(path, '}') + 1;
        if (count <= 1) {
            return path;
        }

        String[] functions = StringHelper.splitOnCharacter(path, "}", count);
        for (String fun : functions) {
            int pos = fun.indexOf("${env:");
            if (pos != -1) {
                String key = fun.substring(pos + 6);
                String value = System.getenv(key);
                if (value != null) {
                    path = path.replace("${env:" + key + "}", value);
                }
            }
            pos = fun.indexOf("${env.");
            if (pos != -1) {
                String key = fun.substring(pos + 6);
                String value = System.getenv(key);
                if (value != null) {
                    path = path.replace("${env." + key + "}", value);
                }
            }
        }

        count = StringHelper.countChar(path, '}') + 1;
        if (count <= 1) {
            return path;
        }
        functions = StringHelper.splitOnCharacter(path, "}", count);
        for (String fun : functions) {
            int pos = fun.indexOf("${");
            if (pos != -1) {
                String key = fun.substring(pos + 2);
                String value = System.getProperty(key);
                if (value != null) {
                    path = path.replace("${" + key + "}", value);
                }
            }
        }

        return path;
    }

}
