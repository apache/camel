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
package org.apache.camel.component.file;

import java.io.File;
import java.util.List;

import org.apache.camel.Processor;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;

/**
 * File consumer.
 */
public class FileConsumer extends GenericFileConsumer<File> {

    private String endpointPath;

    public FileConsumer(GenericFileEndpoint<File> endpoint, Processor processor, GenericFileOperations<File> operations) {
        super(endpoint, processor, operations);
        this.endpointPath = endpoint.getConfiguration().getDirectory();
    }

    protected void pollDirectory(String fileName, List<GenericFile<File>> fileList) {
        File directory = new File(fileName);

        if (!directory.exists() || !directory.isDirectory()) {
            log.warn("Cannot poll directory as file does not exists or is not a directory: " + directory);
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Polling directory: " + directory.getPath());
        }
        File[] files = directory.listFiles();

        if (files == null || files.length == 0) {
            // no files in this directory to poll
            return;
        }
        for (File file : files) {
            // createa a generic file
            GenericFile<File> gf = asGenericFile(endpointPath, file);

            if (file.isDirectory()) {
                if (endpoint.isRecursive() && isValidFile(gf, true)) {
                    // recursive scan and add the sub files and folders
                    String subDirectory = fileName + File.separator + file.getName();
                    pollDirectory(subDirectory, fileList);
                }
            } else if (file.isFile()) {
                if (isValidFile(gf, false)) {
                    // matched file so add
                    fileList.add(gf);
                }
            } else {
                log.debug("Ignoring unsupported file type for file: " + file);
            }
        }
    }

    /**
     * Creates a new GenericFile<File> based on the given file.
     *
     * @param endpointPath the starting directory the endpoint was configued with
     * @param file the source file
     * @return wrapped as a GenericFile
     */
    public static GenericFile<File> asGenericFile(String endpointPath, File file) {
        GenericFile<File> answer = new GenericFile<File>();
        // use file specific binding
        answer.setBinding(new FileBinding());

        answer.setEndpointPath(endpointPath);
        answer.setFile(file);
        answer.setFileName(file.getName());
        answer.setFileNameOnly(file.getName());
        answer.setFileLength(file.length());
        answer.setAbsolute(file.isAbsolute());
        answer.setAbsoluteFilePath(file.getAbsolutePath());
        answer.setLastModified(file.lastModified());
        if (file.isAbsolute()) {
            // use absolute path as relative
            answer.setRelativeFilePath(file.getAbsolutePath());
        } else {
            File path;
            if (file.getPath().startsWith(FileUtil.normalizePath(endpointPath))) {
                // skip duplicate endpoint path
                path = new File(ObjectHelper.after(file.getPath(), FileUtil.normalizePath(endpointPath) + File.separator));
            } else {
                path = new File(file.getPath());
            }

            if (path.getParent() != null) {
                answer.setRelativeFilePath(path.getParent() + File.separator + file.getName());
            } else {
                answer.setRelativeFilePath(path.getName());
            }
        }

        // use file as body as we have converters if needed as stream
        answer.setBody(file);
        return answer;
    }
}
