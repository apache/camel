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
package org.apache.camel.component.file.watch;

import java.nio.file.Path;

import io.methvin.watcher.hashing.FileHash;
import io.methvin.watcher.hashing.FileHasher;

/**
 * For unit test only!
 */
public class TestHasher implements FileHasher {
    @Override
    public FileHash hash(Path path) {
        // Always return constant
        // This should cause every event is triggered only once (hashcode remains the same), so we can test this.
        // Never use this in production code
        return FileHash.fromLong(1L);
    }
}
