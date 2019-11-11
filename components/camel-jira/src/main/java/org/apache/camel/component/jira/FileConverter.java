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
package org.apache.camel.component.jira;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;

@Converter(generateLoader = true)
public final class FileConverter {

    private FileConverter() {
    }

    @Converter
    public static File genericToFile(GenericFile<File> genericFile, Exchange exchange) throws IOException {
        Object body = genericFile.getBody();
        File file;
        if (body instanceof byte[]) {
            byte[] bos = (byte[]) body;
            String destDir = System.getProperty("java.io.tmpdir");
            file = new File(destDir, genericFile.getFileName());
            if (!file.getCanonicalPath().startsWith(destDir)) {
                throw new IOException("File is not jailed to the destination directory");
            }
            Files.write(file.toPath(), bos, StandardOpenOption.CREATE);
            // delete the temporary file on exit, as other routing may need the file for post processing
            file.deleteOnExit();
        } else {
            file = (File) body;
        }
        return file;
    }
}
