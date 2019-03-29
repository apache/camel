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
package org.apache.camel.component.grape;

import java.io.File;
import java.io.FileReader;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;

public class FilePatchesRepository implements PatchesRepository {

    private final File repository;

    public FilePatchesRepository() {
        this(Paths.get(SystemUtils.getUserHome().getAbsolutePath(), ".camel", "patches").toFile());
    }

    public FilePatchesRepository(File repository) {
        try {
            if (!repository.exists()) {
                if (repository.getParentFile() != null) {
                    repository.getParentFile().mkdirs();
                }

                repository.createNewFile();
            }
        } catch (IOException e) {
            throw new IOError(e);
        }

        this.repository = repository;
    }

    @Override
    public void install(String coordinates) {
        try {
            FileUtils.writeStringToFile(repository, coordinates + "\n", true);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    @Override
    public List<String> listPatches() {
        try {
            return IOUtils.readLines(new FileReader(repository));
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    @Override
    public void clear() {
        try {
            repository.delete();
            repository.createNewFile();
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

}
