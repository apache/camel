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

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.util.FileUtil;

/**
 * The Maven to build or run a project with: its Maven wrapper when it has one, and otherwise mvn from the path.
 */
public final class MavenCommand {

    private MavenCommand() {
    }

    /**
     * The Maven wrapper of the directory: mvnw, or mvnw.cmd on Windows (which may not exist).
     */
    public static Path wrapper(Path dir) {
        return dir.resolve(FileUtil.isWindows() ? "mvnw.cmd" : "mvnw");
    }

    /**
     * The Maven wrapper of the directory when it has one that can run, and otherwise mvn.
     */
    public static String of(Path dir) {
        Path wrapper = wrapper(dir);
        return Files.isExecutable(wrapper) ? wrapper.toString() : "mvn";
    }
}
