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
package org.apache.camel.component.azure.blob;

import java.io.InputStream;
import java.util.UUID;

import com.microsoft.azure.storage.blob.BlockEntry;
import com.microsoft.azure.storage.blob.BlockSearchMode;
import com.microsoft.azure.storage.core.Base64;

public class BlobBlock {
    private InputStream blockStream;
    private BlockEntry blockEntry;

    public BlobBlock(InputStream blockStream) {
        this(Base64.encode(UUID.randomUUID().toString().getBytes()),
            blockStream);
    }

    public BlobBlock(String blockId, InputStream blockStream) {
        this(blockId, BlockSearchMode.LATEST, blockStream);
    }

    public BlobBlock(String blockId, BlockSearchMode searchMode, InputStream blockStream) {
        this(new BlockEntry(blockId, searchMode), blockStream);
    }

    public BlobBlock(BlockEntry blockEntry, InputStream blockStream) {
        this.blockStream = blockStream;
        this.blockEntry = blockEntry;
    }

    public InputStream getBlockStream() {
        return blockStream;
    }

    public BlockEntry getBlockEntry() {
        return blockEntry;
    }

}
