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

package org.apache.camel.dsl.jbang.core.common;

public record Source(SourceScheme scheme, String name, String content, String extension, boolean compressed) {

    /**
     * Provides source constant and automatically handles compression of content when enabled.
     *
     * @return the content, maybe compressed.
     */
    public String content() {
        if (compressed()) {
            return CompressionHelper.compressBase64(content);
        }

        return content;
    }

    public String language() {
        if ("yml".equals(extension)) {
            return "yaml";
        }

        return extension;
    }

    public boolean isYaml() {
        return "yaml".equals(language());
    }

    public boolean isXml() {
        return "xml".equals(language());
    }
}
