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
package org.apache.camel.maven.dsl.yaml.support;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

public final class ToolingSupport {
    private ToolingSupport() {
    }

    /**
     * Combines the given items assuming they can be also composed by comma separated elements.
     *
     * @param  items the items
     * @return       a stream of individual items
     */
    public static Stream<String> combine(String... items) {
        Set<String> answer = new TreeSet<>();

        for (String item : items) {
            if (item == null) {
                continue;
            }

            String[] elements = item.split(",");
            answer.addAll(Arrays.asList(elements));
        }

        return answer.stream();
    }

    public static void mkparents(File path) throws IOException {
        if (!path.getParentFile().exists() && !path.getParentFile().mkdirs()) {
            throw new IOException("Unable to create directory " + path.getParentFile());
        }
    }
}
