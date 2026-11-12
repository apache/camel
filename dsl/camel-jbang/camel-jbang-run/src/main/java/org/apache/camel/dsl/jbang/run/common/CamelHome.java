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
package org.apache.camel.dsl.jbang.run.common;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.camel.util.HomeHelper;

/**
 * The directories of the Camel CLI: the user home directory, the Camel directory in it, and the working directory.
 */
public final class CamelHome {

    public static final String CAMEL_DIR = ".camel";
    public static final String CAMEL_JBANG_WORK_DIR = ".camel-jbang";

    private static volatile Path homeDir = Paths.get(HomeHelper.resolveHomeDir());
    private static volatile Path workDir;

    private CamelHome() {
    }

    /**
     * Gets the user home directory.
     *
     * @return the user home directory.
     */
    public static Path getHomeDir() {
        return homeDir;
    }

    /**
     * Adjust basic home directory where user properties and other settings will be stored. Unit tests may set this in
     * order to create stable and independent tests.
     *
     * @param homeDir the home directory.
     */
    public static void useHomeDir(String homeDir) {
        CamelHome.homeDir = Paths.get(homeDir);
    }

    /**
     * The basic Camel directory located in the user home directory.
     *
     * @return file pointing to the camel directory.
     */
    public static Path getCamelDir() {
        return homeDir.resolve(CAMEL_DIR);
    }

    /**
     * Gets the Camel CLI working directory. By default this is the {@value #CAMEL_JBANG_WORK_DIR} directory in the
     * current working directory; it can be overridden with {@link #useWorkDir(Path)} so that parallel test instances
     * each have an isolated directory of their own.
     *
     * @return file pointing to the working directory.
     */
    public static Path getWorkDir() {
        Path override = workDir;
        return override != null ? override : Paths.get(CAMEL_JBANG_WORK_DIR);
    }

    /**
     * Override the working directory. Unit tests call this in {@code @BeforeEach} to give each test instance an
     * isolated directory, then call {@link #resetWorkDir()} in {@code @AfterEach} to restore the default.
     *
     * @param workDir the working directory to use.
     */
    public static void useWorkDir(Path workDir) {
        CamelHome.workDir = workDir;
    }

    /**
     * Reset the working directory to the default ({@value #CAMEL_JBANG_WORK_DIR} in the current working directory).
     */
    public static void resetWorkDir() {
        CamelHome.workDir = null;
    }
}
