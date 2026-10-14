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
package org.apache.camel.dsl.jbang.core.commands.validate;

import java.nio.file.Path;
import java.util.Map;

import org.apache.camel.impl.DefaultDumpRoutesStrategy;
import org.apache.camel.spi.Resource;

/**
 * Dumps the routes for {@code camel validate normalize}: first one file per input file into a directory (each file
 * named as the command says), then all the routes merged into one file. The merged file is written last, so once it
 * exists the dump is complete.
 */
class NormalizeDumpRoutesStrategy extends DefaultDumpRoutesStrategy {

    private final Path directory;
    private final Path merged;
    private final Map<Path, String> names;
    private final String unattributed;
    private boolean perFile;

    /**
     * @param directory    the directory to dump one file per input file into
     * @param merged       the file to dump all the routes into
     * @param names        the file name in the directory of each input file, by its absolute normalized path
     * @param unattributed the file name in the directory for what was not loaded from one of the input files
     */
    NormalizeDumpRoutesStrategy(Path directory, Path merged, Map<Path, String> names, String unattributed) {
        this.directory = directory;
        this.merged = merged;
        this.names = names;
        this.unattributed = unattributed;
    }

    @Override
    public void setOutput(String output) {
        // the outputs are set by dumpRoutes: once the output is set to a file, it stays a file, so the output of the
        // main configuration (camel.main.dumpRoutesOutput) is ignored
    }

    @Override
    public void dumpRoutes(String format) {
        // the per-file dump first, as it needs a directory
        perFile = true;
        super.setOutput(directory.toString());
        super.dumpRoutes(format);
        perFile = false;
        super.setOutput(merged.toString());
        super.dumpRoutes(format);
    }

    @Override
    protected String resolveFileName(String ext, Resource resource) {
        if (!perFile) {
            return super.resolveFileName(ext, resource);
        }
        Path source = sourceOf(resource);
        String name = source != null ? names.get(source) : null;
        return name != null ? name : unattributed;
    }

    /**
     * The absolute normalized path of the file a resource was loaded from, or null when it was not loaded from a file
     */
    static Path sourceOf(Resource resource) {
        String location = resource != null ? resource.getLocation() : null;
        if (location == null || !location.startsWith("file:")) {
            return null;
        }
        location = location.startsWith("file://") ? location.substring(7) : location.substring(5);
        try {
            return Path.of(location).toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }
}
