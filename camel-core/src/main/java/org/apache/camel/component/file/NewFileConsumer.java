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

/**
 *
 */
public class NewFileConsumer extends GenericFileConsumer<File> {

    public NewFileConsumer(GenericFileEndpoint<File> endpoint, Processor processor, GenericFileOperations<File> operations) {
        super(endpoint, processor, operations);
    }

    protected void pollDirectory(String fileName, List<GenericFile<File>> fileList) {
        File fileOrDirectory = new File(fileName);

        if (!fileOrDirectory.exists()) {
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Polling directory: " + fileOrDirectory.getPath());
        }
        File[] files = fileOrDirectory.listFiles();
        for (File file : files) {
            // createa a generic file
            GenericFile<File> gf = asGenericFile(file);

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
        GenericFile<File> gf = asGenericFile(file);

        if (isValidFile(gf, false)) {
            // matched file so add
            fileList.add(gf);
        }

    }

    protected GenericFile<File> asGenericFile(File file) {
        GenericFile<File> answer = new GenericFile<File>();
        answer.setFile(file);
        answer.setFileLength(file.length());
        answer.setFileName(file.getName());
        answer.setAbsoluteFileName(file.getAbsolutePath());
        answer.setLastModified(file.lastModified());
        if (file.getParent() != null) {
            answer.setRelativeFileName(file.getParent() + "/" + file.getName());
        } else {
            answer.setRelativeFileName(file.getName());
        }
        // use file as body as we have converters if needed as stream
        answer.setBody(file);
        return answer;
    }
}
