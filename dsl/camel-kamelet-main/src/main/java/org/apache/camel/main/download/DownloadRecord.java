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
package org.apache.camel.main.download;

import org.apache.camel.spi.Metadata;

/**
 * Record for details when an artifact was downloaded from a remote Maven repository.
 */
public record DownloadRecord(
        @Metadata(description = "The Maven group id") String groupId,
        @Metadata(description = "The Maven artifact id") String artifactId,
        @Metadata(description = "The Maven version") String version,
        @Metadata(description = "The Maven repository id the artifact was downloaded from") String repoId,
        @Metadata(description = "The Maven repository URL the artifact was downloaded from") String repoUrl,
        @Metadata(description = "The download time in milliseconds") long elapsed) {

}
