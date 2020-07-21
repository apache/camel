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

public final class GenericFileHelper {

    private GenericFileHelper() {
    }

    public static String asExclusiveReadLockKey(GenericFile file, String key) {
        // use the copy from absolute path as that was the original path of the
        // file when the lock was acquired
        // for example if the file consumer uses preMove then the file is moved
        // and therefore has another name
        // that would no longer match
        String path = file.getCopyFromAbsoluteFilePath() != null ? file.getCopyFromAbsoluteFilePath() : file.getAbsoluteFilePath();
        return asExclusiveReadLockKey(path, key);
    }

    public static String asExclusiveReadLockKey(String path, String key) {
        return path + "-" + key;
    }

}
