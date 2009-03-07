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
import java.io.IOException;
import java.util.List;

import org.apache.camel.Processor;
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
        File fileOrDirectory = new File(fileName);

        if (!fileOrDirectory.exists()) {
            return;
        }

        // could be a file and not a directory so delegate to poll file instead
        // this happens if end user has specified a filename in the URI but have not
        // set directory=false as an option
        if (fileOrDirectory.isFile()) {
            pollFile(fileName, fileList);
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Polling directory: " + fileOrDirectory.getPath());
        }
        File[] files = fileOrDirectory.listFiles();

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
                    String directory = fileName + "/" + file.getName();
                    pollDirectory(directory, fileList);
                }
            } else if (file.isFile()) {
                if (isValidFile(gf, false)) {
                    // matched file so add
                    fileList.add(gf);
                }
            } else {
                log.debug("Ignoring unsupported file type " + file);
            }
        }
    }

    protected void pollFile(String fileName, List<GenericFile<File>> fileList) {
        File file = new File(fileName);

        if (!file.exists()) {
            return;
        }

        // createa a generic file
        GenericFile<File> gf = asGenericFile(endpointPath, file);

        if (isValidFile(gf, false)) {
            // matched file so add
            fileList.add(gf);
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
        answer.setEndpointPath(endpointPath);
        answer.setBinding(new FileBinding());
        answer.setFile(file);
        answer.setFileLength(file.length());
        answer.setFileName(file.getName());
        answer.setAbsolute(file.isAbsolute());
        answer.setAbsoluteFileName(file.getAbsolutePath());
        try {
            answer.setCanonicalFileName(file.getCanonicalPath());
        } catch (IOException e) {
            // ignore
        }
        answer.setLastModified(file.lastModified());
        if (file.isAbsolute()) {
            answer.setRelativeFileName(null);
        } else {
            File path;
            if (file.getPath().startsWith(endpointPath)) {
                // skip duplicate endpoint path
                path = new File(ObjectHelper.after(file.getPath(), endpointPath + File.separator));
            } else {
                path = new File(file.getPath());
            }

            if (path.getParent() != null) {
                answer.setRelativeFileName(path.getParent() + File.separator + file.getName());
            } else {
                answer.setRelativeFileName(path.getName());
            }
        }
        // use file as body as we have converters if needed as stream
        answer.setBody(file);
        return answer;
    }
}
