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
package org.apache.camel.component.whatsapp.util;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.whatsapp.model.UploadMedia;

public class FileUploadStreamSupplier {

    private InputStream preFileIS;
    private InputStream fileIS;
    private InputStream postFileIS;
    private final Map<Object, Object> data;
    private final String boundary;

    public FileUploadStreamSupplier(Map<Object, Object> data, String boundary) {
        this.data = data;
        this.boundary = boundary;

        List<byte[]> byteArrays = new ArrayList<>();
        byte[] boundaryBytes = boundary.getBytes();
        ByteBuffer separatorBuffer = RestAdapterUtils.generateByteBuffer(RestAdapterUtils.EXTRA_BYTES, boundaryBytes,
                RestAdapterUtils.NEW_LINE_BYTES,
                RestAdapterUtils.CONTENT_DISPOSITION_BYTES);

        byte[] separator = separatorBuffer.array();

        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            byteArrays.add(separator);

            byte[] keyBytes = ((String) entry.getKey()).getBytes();

            if (entry.getValue() instanceof UploadMedia) {
                UploadMedia uploadMedia = (UploadMedia) entry.getValue();

                generateUploadMediaStream(byteArrays, keyBytes, uploadMedia);
            } else {
                byte[] entryStringBytes = entry.getValue().toString().getBytes();
                ByteBuffer stringBuffer = RestAdapterUtils.generateByteBuffer(RestAdapterUtils.QUOTE_BYTES, keyBytes,
                        RestAdapterUtils.NEW_LINE_BYTES,
                        RestAdapterUtils.NEW_LINE_BYTES, entryStringBytes, RestAdapterUtils.NEW_LINE_BYTES);

                byteArrays.add(stringBuffer.array());
            }
        }
        ByteBuffer endBuffer = RestAdapterUtils.generateByteBuffer(RestAdapterUtils.EXTRA_BYTES, boundaryBytes,
                RestAdapterUtils.EXTRA_BYTES, RestAdapterUtils.NEW_LINE_BYTES);

        byteArrays.add(endBuffer.array());

        ByteBuffer postFileBytes = RestAdapterUtils.generateByteBuffer(byteArrays);
        postFileIS = new ByteArrayInputStream(postFileBytes.array());
    }

    private void generateUploadMediaStream(List<byte[]> byteArrays, byte[] keyBytes, UploadMedia uploadMedia) {
        byte[] fileNameBytes;
        if (uploadMedia.getFile() != null) {
            fileNameBytes = uploadMedia.getFile().toPath().getFileName().toString().getBytes();

            try {
                fileIS = new FileInputStream(uploadMedia.getFile());
            } catch (FileNotFoundException e) {
                throw new RuntimeCamelException(e);
            }
        } else {
            fileNameBytes = uploadMedia.getName().getBytes();
            fileIS = uploadMedia.getFileStream();
        }

        byte[] contentTypeBytes = uploadMedia.getContentType().getBytes();
        ByteBuffer uploadMediaBuffer = RestAdapterUtils.generateByteBuffer(RestAdapterUtils.QUOTE_BYTES, keyBytes,
                RestAdapterUtils.FILE_NAME_HEADER_BYTES, fileNameBytes,
                RestAdapterUtils.CONTENT_TYPE_HEADER_BYTES, contentTypeBytes, RestAdapterUtils.NEW_LINE_BYTES,
                RestAdapterUtils.NEW_LINE_BYTES);

        byteArrays.add(uploadMediaBuffer.array());

        ByteBuffer preFileBytes = RestAdapterUtils.generateByteBuffer(byteArrays);

        preFileIS = new ByteArrayInputStream(preFileBytes.array());

        byteArrays.clear();
        byteArrays.add(RestAdapterUtils.NEW_LINE_BYTES);
    }

    public Supplier<? extends InputStream> generate() {
        return (Supplier<SequenceInputStream>) () -> new SequenceInputStream(
                preFileIS, new SequenceInputStream(fileIS, postFileIS));
    }

    public Map<Object, Object> getData() {
        return data;
    }

    public String getBoundary() {
        return boundary;
    }
}
