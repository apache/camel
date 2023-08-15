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
package org.apache.camel.maven.htmlxlsx.process;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;

public class FileUtil {

    private static final String OUTPUT_FILE = "%s.html";

    public String getLastElementOfPath(String path) {

        return Paths.get(path).getFileName().toString();
    }

    public String readFile(final String inputFile) throws IOException {

        Path inputFilePath = Paths.get(inputFile);

        return Files.readString(Paths.get(inputFilePath.toString()));
    }

    public String write(final String rendered, final String inputFileName, final File outputPath) throws IOException {

        Path writeOutputPath = outputFile(inputFileName, outputPath.getPath());

        Files.writeString(writeOutputPath, rendered);

        return writeOutputPath.toString();
    }

    public void write(final String content, final Path outputPath) throws IOException {

        outputPath.getParent().toFile().mkdirs();

        Files.writeString(outputPath, content);
    }

    public Path outputFile(final String inputFileName, final String outputPath) {

        Path outputFileName = Paths.get(String.format(OUTPUT_FILE, removeFileExtension(inputFileName)));

        return Paths.get(outputPath, outputFileName.toString());
    }

    public String removeFileExtension(String filename) {

        if (filename == null || filename.isEmpty()) {
            return filename;
        }

        String extPattern = "(?<!^)[.]" + "[^.]*$";

        return filename.replaceAll(extPattern, "");
    }

    public Set<String> filesInDirectory(File dir) throws IOException {

        Set<String> fileList = new HashSet<>();
        Path dirPath = Paths.get(dir.getPath());
        if (!Files.exists(dirPath)) {
            return Collections.emptySet();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
            for (Path path : stream) {
                if (!Files.isDirectory(path)) {
                    fileList.add(path.toString());
                }
            }
        }

        return fileList;
    }

    public String readFileFromClassPath(String path) throws IOException {

        return IOUtils.resourceToString(path, Charset.defaultCharset(), FileUtil.class.getClassLoader());
    }
}
