/**
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
package org.apache.camel.component.grape

import org.apache.commons.lang3.SystemUtils

import java.nio.file.Paths

import static org.apache.commons.io.FileUtils.writeStringToFile
import static org.apache.commons.io.IOUtils.readLines

class FilePatchesRepository implements PatchesRepository {

    private final File repository

    FilePatchesRepository() {
        this(Paths.get(SystemUtils.userHome.absolutePath, ".camel", "patches").toFile())
    }

    FilePatchesRepository(File repository) {
        if(!repository.exists()) {
            if(repository.getParentFile() != null) {
                repository.getParentFile().mkdirs()
            }
            repository.createNewFile()
        }
        this.repository = repository
    }

    @Override
    void install(String coordinates) {
        writeStringToFile(repository, coordinates + "\n", true);
    }

    @Override
    List<String> listPatches() {
        readLines(new FileReader(repository))
    }

    @Override
    void clear() {
        repository.delete()
        repository.createNewFile()
    }

}
