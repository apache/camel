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
package org.apache.camel.support;

import org.apache.camel.CamelContext;
import org.apache.camel.util.FilePathResolver;
import org.apache.camel.util.FileUtil;

/**
 * Helper for resolving temp directory.
 */
public final class TempDirHelper {

    public static final String DEFAULT_PATTERN = "${java.io.tmpdir}/camel/camel-tmp-#uuid#";

    private TempDirHelper() {
    }

    /**
     * Resolves a temp dir using the default pattern.
     *
     * @param  camelContext the camel context
     * @param  path         the sub-dir for the temp dir
     * @return              the resolved temp dir
     */
    public static String resolveDefaultTempDir(CamelContext camelContext, String path) {
        return resolveTempDir(camelContext, DEFAULT_PATTERN, path);
    }

    /**
     * Resolves a temp dir
     *
     * @param  camelContext the camel context
     * @param  pattern      pattern for the base path of the temp dir
     * @param  path         the sub-dir for the temp dir
     * @return              the resolved temp dir
     */
    public static String resolveTempDir(CamelContext camelContext, String pattern, String path) {
        String answer;
        if (pattern != null && path != null) {
            path = pattern + "/" + path;
        } else if (path == null) {
            path = pattern;
        }
        if (camelContext.getManagementNameStrategy() != null) {
            String name = camelContext.getManagementNameStrategy().resolveManagementName(path, camelContext.getName(), false);
            if (name != null) {
                name = customResolveManagementName(camelContext, name);
            }
            // and then check again with invalid check to ensure all ## is resolved
            if (name != null) {
                name = camelContext.getManagementNameStrategy().resolveManagementName(name, camelContext.getName(), true);
            }
            answer = name;
        } else {
            answer = defaultManagementName(camelContext, path);
        }
        // remove double slashes
        answer = FileUtil.compactPath(answer);
        return answer;
    }

    private static String defaultManagementName(CamelContext camelContext, String path) {
        // must quote the names to have it work as literal replacement
        String name = camelContext.getName();

        // replace tokens
        String answer = path;
        answer = answer.replace("#camelId#", name);
        answer = answer.replace("#name#", name);
        // replace custom
        answer = customResolveManagementName(camelContext, answer);
        return answer;
    }

    private static String customResolveManagementName(CamelContext camelContext, String pattern) {
        if (pattern.contains("#uuid#")) {
            String uuid = camelContext.getUuidGenerator().generateUuid();
            pattern = pattern.replace("#uuid#", uuid);
        }
        return FilePathResolver.resolvePath(pattern);
    }

}
