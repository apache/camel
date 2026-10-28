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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;

/**
 * Tells a project folder from a folder that is not one project. The AI project overview explains one project: started
 * in the home folder, or in a workspace with a checkout of each repository, it would explain the routes it happens to
 * find across all of them as if they were one.
 */
final class ProjectFolders {

    /** What makes a folder a project of its own: a repository, a build, or a Camel configuration. */
    private static final List<String> PROJECT_FILES = List.of(
            ".git", "pom.xml", "build.gradle", "build.gradle.kts", "application.properties", "application.yaml",
            "application.yml");
    /** The route files a Camel JBang project keeps at the top of its folder. */
    private static final List<String> ROUTE_EXTENSIONS = List.of(".yaml", ".yml", ".xml", ".java", ".groovy");
    /** How many of the projects a folder holds are named. */
    private static final int NAMED = 3;

    private ProjectFolders() {
    }

    /**
     * Why a folder is not one project to explain, or null when it is one. It is not when it is the home folder or a
     * root of the file system, or when it is no project itself (no repository, build, Camel configuration or route file
     * at its top) while two or more of its subfolders are, as in a workspace of checkouts.
     */
    static String notOneProject(Path dir) {
        return notOneProject(dir, CommandLineHelper.getHomeDir());
    }

    /** As {@link #notOneProject(Path)}, with the home folder given. */
    static String notOneProject(Path dir, Path home) {
        if (dir == null) {
            return null;
        }
        Path path = dir.toAbsolutePath().normalize();
        if (path.getParent() == null) {
            return path + " is the root of the file system";
        }
        if (home != null && path.equals(home.toAbsolutePath().normalize())) {
            return AcpHeaderStrip.homeRelative(path) + " is your home folder";
        }
        if (isProject(path, true)) {
            return null;
        }
        List<String> projects = new ArrayList<>();
        try (Stream<Path> children = Files.list(path)) {
            children.filter(Files::isDirectory)
                    .filter(d -> !d.getFileName().toString().startsWith("."))
                    .filter(d -> isProject(d, false))
                    .map(d -> d.getFileName().toString())
                    .sorted()
                    .forEach(projects::add);
        } catch (IOException e) {
            return null;
        }
        if (projects.size() < 2) {
            return null;
        }
        String named = String.join(", ", projects.subList(0, Math.min(NAMED, projects.size())));
        if (projects.size() > NAMED) {
            named += " and " + (projects.size() - NAMED) + " more";
        }
        return AcpHeaderStrip.homeRelative(path) + " holds " + projects.size() + " projects (" + named + ")";
    }

    /**
     * Whether a folder is a project: a repository, build or Camel configuration at its top, or (when
     * {@code routeFiles}) a route file there. Subfolders are not judged by route files, as a project may keep its
     * routes in a few folders of its own.
     */
    private static boolean isProject(Path dir, boolean routeFiles) {
        for (String name : PROJECT_FILES) {
            if (Files.exists(dir.resolve(name))) {
                return true;
            }
        }
        if (!routeFiles) {
            return false;
        }
        try (Stream<Path> children = Files.list(dir)) {
            return children.filter(Files::isRegularFile)
                    .map(f -> f.getFileName().toString().toLowerCase(Locale.ROOT))
                    .anyMatch(n -> !n.startsWith(".") && ROUTE_EXTENSIONS.stream().anyMatch(n::endsWith));
        } catch (IOException e) {
            return true;
        }
    }
}
