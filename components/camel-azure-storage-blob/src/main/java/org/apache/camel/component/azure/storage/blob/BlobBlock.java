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
package org.apache.camel.component.azure.storage.blob;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import com.azure.core.util.Base64Util;
import com.azure.storage.blob.models.Block;

public final class BlobBlock {
    private final InputStream blockStream;
    private final Block blockEntry;

    private BlobBlock(Block blockEntry, InputStream blockStream) {
        this.blockStream = blockStream;
        this.blockEntry = blockEntry;
    }

    public static BlobBlock createBlobBlock(final InputStream inputStream) throws IOException {
        return createBlobBlock(Base64Util.encodeToString(UUID.randomUUID().toString().getBytes()), inputStream);
    }

    public static BlobBlock createBlobBlock(final String blockId, final InputStream inputStream) throws IOException {
        return createBlobBlock(blockId, BlobUtils.getInputStreamLength(inputStream), inputStream);
    }

    public static BlobBlock createBlobBlock(final String blockId, final long size, final InputStream inputStream) {
        final Block block = new Block().setName(blockId).setSizeLong(size);

        return new BlobBlock(block, inputStream);
    }

    public InputStream getBlockStream() {
        return blockStream;
    }

    public Block getBlockEntry() {
        return blockEntry;
    }
}
