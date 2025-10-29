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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents metadata extracted from a document.
 */
public class DocumentMetadata {

    private String title;
    private String author;
    private String creator;
    private String producer;
    private String subject;
    private String keywords;
    private Instant creationDate;
    private Instant modificationDate;
    private Integer pageCount;
    private String language;
    private String documentType;
    private String format;
    private Long fileSizeBytes;
    private String fileName;
    private String filePath;
    private Map<String, Object> customMetadata;
    private Map<String, Object> rawMetadata;

    public DocumentMetadata() {
        this.customMetadata = new HashMap<>();
        this.rawMetadata = new HashMap<>();
    }

    /**
     * Gets the document title.
     *
     * @return document title
     */
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Gets the document author.
     *
     * @return author name
     */
    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    /**
     * Gets the creator (application that created the document).
     *
     * @return creator application name
     */
    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    /**
     * Gets the producer (application that produced the PDF, if applicable).
     *
     * @return producer application name
     */
    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    /**
     * Gets the document subject.
     *
     * @return document subject
     */
    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * Gets the document keywords.
     *
     * @return keywords as a comma-separated string
     */
    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    /**
     * Gets the document creation date.
     *
     * @return creation date
     */
    public Instant getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Instant creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Gets the document modification date.
     *
     * @return modification date
     */
    public Instant getModificationDate() {
        return modificationDate;
    }

    public void setModificationDate(Instant modificationDate) {
        this.modificationDate = modificationDate;
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
     * Gets the document language.
     *
     * @return language code (e.g., "en", "fr", "de")
     */
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    /**
     * Gets the document type/format.
     *
     * @return document type (e.g., "PDF", "DOCX", "PPTX")
     */
    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
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
     * Gets custom metadata fields.
     *
     * @return map of custom metadata fields
     */
    public Map<String, Object> getCustomMetadata() {
        return customMetadata;
    }

    public void setCustomMetadata(Map<String, Object> customMetadata) {
        this.customMetadata = customMetadata != null ? customMetadata : new HashMap<>();
    }

    /**
     * Adds a custom metadata field.
     *
     * @param key   the field name
     * @param value the field value
     */
    public void addCustomMetadata(String key, Object value) {
        if (this.customMetadata == null) {
            this.customMetadata = new HashMap<>();
        }
        this.customMetadata.put(key, value);
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
     * Checks if the metadata has a title.
     *
     * @return true if title is present
     */
    public boolean hasTitle() {
        return title != null && !title.isEmpty();
    }

    /**
     * Checks if the metadata has an author.
     *
     * @return true if author is present
     */
    public boolean hasAuthor() {
        return author != null && !author.isEmpty();
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
        return "DocumentMetadata{" + "title='" + title + '\'' + ", author='" + author + '\'' + ", creator='" + creator + '\''
               + ", producer='" + producer + '\'' + ", subject='" + subject + '\'' + ", keywords='" + keywords + '\''
               + ", creationDate=" + creationDate + ", modificationDate=" + modificationDate + ", pageCount=" + pageCount
               + ", language='" + language + '\'' + ", documentType='" + documentType + '\'' + ", format='" + format + '\''
               + ", fileSizeBytes=" + fileSizeBytes + ", fileName='" + fileName + '\'' + ", filePath='" + filePath + '\''
               + ", customMetadataFields=" + (customMetadata != null ? customMetadata.size() : 0) + ", rawMetadataFields="
               + (rawMetadata != null ? rawMetadata.size() : 0) + '}';
    }

}
