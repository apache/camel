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
package org.apache.camel.component.file;

import java.io.File;
import java.util.function.Supplier;

import org.apache.camel.Exchange;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.FileUtil;

public final class GenericFileHelper {

    private GenericFileHelper() {
    }

    /**
     * Ensures the resolved local work file stays within the configured local work directory. The remote file name used
     * to build the local work file path may contain {@code ../} sequences that would otherwise resolve to a path
     * outside the work directory.
     *
     * @param  target                              the resolved local work file (or its in-progress temp file)
     * @param  localWorkDirectory                  the local work directory the file must stay within
     * @throws GenericFileOperationFailedException if the target resolves outside the local work directory
     */
    public static void jailToLocalWorkDirectory(File target, File localWorkDirectory) {
        // compact first as the remote relative name can use ../ etc
        String compactTarget = FileUtil.compactPath(target.getPath());
        String compactWork = FileUtil.compactPath(localWorkDirectory.getPath());
        if (!compactTarget.startsWith(compactWork)) {
            throw new GenericFileOperationFailedException(
                    "Cannot retrieve file to local work file: " + compactTarget
                                                          + " as it is jailed to the local work directory: "
                                                          + compactWork);
        }
    }

    public static String asExclusiveReadLockKey(GenericFile file, String key) {
        // use the copy from absolute path as that was the original path of the
        // file when the lock was acquired
        // for example if the file consumer uses preMove then the file is moved
        // and therefore has another name
        // that would no longer match
        String path
                = file.getCopyFromAbsoluteFilePath() != null ? file.getCopyFromAbsoluteFilePath() : file.getAbsoluteFilePath();
        return asExclusiveReadLockKey(path, key);
    }

    public static String asExclusiveReadLockKey(String path, String key) {
        return path + "-" + key;
    }

    public static <T> Exchange createDummy(GenericFileEndpoint<T> endpoint, Exchange dynamic, Supplier<GenericFile<T>> file) {
        Exchange dummy = endpoint.createExchange(file.get());
        if (dynamic != null) {
            // enrich with data from dynamic source
            if (dynamic.getMessage().hasHeaders()) {
                MessageHelper.copyHeaders(dynamic.getMessage(), dummy.getMessage(), true);
                if (dynamic.hasVariables()) {
                    dummy.getVariables().putAll(dynamic.getVariables());
                }
            }
        }
        return dummy;
    }

}
