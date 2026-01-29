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
package org.apache.camel.component.docling;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents metadata extracted from a document.
 */
public class DocumentMetadata {

    private Integer pageCount;
    private String format;
    private Long fileSizeBytes;
    private String fileName;
    private String filePath;
    private Map<String, Object> rawMetadata;

    public DocumentMetadata() {
        this.rawMetadata = new HashMap<>();
    }

    /**
     * Gets the number of pages in the document.
     *
     * @return page count
     */
    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    /**
     * Gets the MIME type or format identifier.
     *
     * @return format (e.g., "application/pdf")
     */
    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    /**
     * Gets the file size in bytes.
     *
     * @return file size
     */
    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    /**
     * Gets the file name.
     *
     * @return file name
     */
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Gets the file path.
     *
     * @return file path
     */
    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    /**
     * Gets the raw metadata as returned by the parser.
     *
     * @return map of raw metadata
     */
    public Map<String, Object> getRawMetadata() {
        return rawMetadata;
    }

    public void setRawMetadata(Map<String, Object> rawMetadata) {
        this.rawMetadata = rawMetadata != null ? rawMetadata : new HashMap<>();
    }

    /**
     * Adds a raw metadata field.
     *
     * @param key   the field name
     * @param value the field value
     */
    public void addRawMetadata(String key, Object value) {
        if (this.rawMetadata == null) {
            this.rawMetadata = new HashMap<>();
        }
        this.rawMetadata.put(key, value);
    }

    /**
     * Checks if the metadata has a page count.
     *
     * @return true if page count is present
     */
    public boolean hasPageCount() {
        return pageCount != null && pageCount > 0;
    }

    @Override
    public String toString() {
        return "DocumentMetadata{"
               + ", pageCount=" + pageCount
               + ", format='" + format + '\''
               + ", fileSizeBytes=" + fileSizeBytes + ", fileName='" + fileName + '\'' + ", filePath='" + filePath + '\''
               + ", rawMetadataFields="
               + (rawMetadata != null ? rawMetadata.size() : 0) + '}';
    }

}
