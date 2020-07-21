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
package org.apache.maven.plugins.javadoc;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Helper class to compute and write data used to detect a stale javadoc.
 */
public final class StaleHelper {
    
    private StaleHelper() {
    }

    /**
     * Compute the data used to detect a stale javadoc
     *
     * @param cmd the command line
     * @return the stale data
     * @throws MavenReportException if an error occurs
     */
    public static String getStaleData(Commandline cmd) throws MavenReportException {
        try {
            List<String> ignored = new ArrayList<>();
            List<String> options = new ArrayList<>();
            Path dir = cmd.getWorkingDirectory().toPath().toAbsolutePath().normalize();
            String[] args = cmd.getCommandline();
            Collections.addAll(options, args);
            for (String arg : args) {
                if (arg.startsWith("@")) {
                    String name = arg.substring(1);
                    options.addAll(Files.readAllLines(dir.resolve(name), StandardCharsets.UTF_8));
                    ignored.add(name);
                }
            }
            List<String> state = new ArrayList<>(options);
            boolean cp = false;
            boolean sp = false;
            for (String arg : options) {
                if (cp) {
                    String s = unquote(arg);
                    for (String ps : s.split(File.pathSeparator)) {
                        Path p = dir.resolve(ps);
                        state.add(p + " = " + lastmod(p));
                    }
                } else if (sp) {
                    String s = unquote(arg);
                    for (String ps : s.split(File.pathSeparator)) {
                        Path p = dir.resolve(ps);
                        for (Path c : walk(p)) {
                            if (Files.isRegularFile(c)) {
                                state.add(c + " = " + lastmod(c));
                            }
                        }
                        state.add(p + " = " + lastmod(p));
                    }
                }
                cp = "-classpath".equals(arg);
                sp = "-sourcepath".equals(arg);
            }
            for (Path p : walk(dir)) {
                if (Files.isRegularFile(p) && !ignored.contains(p.getFileName().toString())) {
                    state.add(p + " = " + lastmod(p));
                }
            }
            return StringUtils.join(state.iterator(), SystemUtils.LINE_SEPARATOR);
        } catch (Exception e) {
            throw new MavenReportException("Unable to compute stale date", e);
        }
    }

    /**
     * Write the data used to detect a stale javadoc
     *
     * @param cmd the command line
     * @param path the stale data path
     * @throws MavenReportException if an error occurs
     */
    public static void writeStaleData(Commandline cmd, Path path) throws MavenReportException {
        try {
            String curdata = getStaleData(cmd);
            Files.createDirectories(path.getParent());
            FileUtils.fileWrite(path.toFile(), null /* platform encoding */, curdata);
        } catch (IOException e) {
            throw new MavenReportException("Error checking stale data", e);
        }
    }

    private static Collection<Path> walk(Path dir) {
        try {
            Collection<Path> paths = new ArrayList<>();
            for (Path p : Files.newDirectoryStream(dir)) {
                paths.add(p);
            }
            return paths;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String unquote(String s) {
        if (s.startsWith("'") && s.endsWith("'")) {
            return s.substring(1, s.length() - 1).replaceAll("\\\\'", "'");
        } else {
            return s;
        }
    }

    private static long lastmod(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

}
