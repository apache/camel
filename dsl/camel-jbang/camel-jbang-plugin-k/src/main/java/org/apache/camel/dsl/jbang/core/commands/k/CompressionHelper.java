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

package org.apache.camel.dsl.jbang.core.commands.k;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.apache.camel.RuntimeCamelException;

/**
 * Utility helper to handle base64 compression of sources.
 */
public class CompressionHelper {

    private CompressionHelper() {
        // prevent instantiation of utility class.
    }

    /**
     * Compress given data with deflate and base64 encoding.
     *
     * @param  data to be compressed.
     * @return      compressed base64 encoded data
     */
    public static String compressBase64(String data) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Deflater compressor = new Deflater(Deflater.BEST_COMPRESSION, true);
        try (DeflaterOutputStream dos = new DeflaterOutputStream(bos, compressor, true)) {
            dos.write(data.getBytes(StandardCharsets.UTF_8));
            dos.flush();
            return new String(Base64.getEncoder().encode(bos.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeCamelException("Failed to compress data", e);
        }
    }
}
