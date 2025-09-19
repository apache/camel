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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Configuration for the Docling component.
 */
@Configurer
@UriParams
public class DoclingConfiguration implements Cloneable {

    @UriParam
    @Metadata(required = true, defaultValue = "CONVERT_TO_MARKDOWN", description = "The operation to perform")
    private DoclingOperations operation = DoclingOperations.CONVERT_TO_MARKDOWN;

    @UriParam(label = "advanced")
    @Metadata(description = "Path to Docling Python executable or command")
    private String doclingCommand = "docling";

    @UriParam(label = "advanced")
    @Metadata(description = "Working directory for Docling execution")
    private String workingDirectory;

    @UriParam
    @Metadata(description = "Enable OCR processing for scanned documents", defaultValue = "true")
    private boolean enableOCR = true;

    @UriParam
    @Metadata(description = "Language code for OCR processing", defaultValue = "en")
    private String ocrLanguage = "en";

    @UriParam
    @Metadata(description = "Output format for document conversion", defaultValue = "markdown")
    private String outputFormat = "markdown";

    @UriParam(label = "advanced")
    @Metadata(description = "Timeout for Docling process execution in milliseconds", defaultValue = "30000")
    private long processTimeout = 30000;

    @UriParam
    @Metadata(description = "Show layout information with bounding boxes", defaultValue = "false")
    private boolean includeLayoutInfo = false;

    @UriParam(label = "security")
    @Metadata(description = "Maximum file size in bytes for processing", defaultValue = "52428800")
    private long maxFileSize = 50 * 1024 * 1024; // 50MB

    @UriParam
    @Metadata(description = "Include the content of the output file in the exchange body and delete the output file",
              defaultValue = "false")
    private boolean contentInBody = false;

    public DoclingOperations getOperation() {
        return operation;
    }

    public void setOperation(DoclingOperations operation) {
        this.operation = operation;
    }

    public String getDoclingCommand() {
        return doclingCommand;
    }

    public void setDoclingCommand(String doclingCommand) {
        this.doclingCommand = doclingCommand;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public boolean isEnableOCR() {
        return enableOCR;
    }

    public void setEnableOCR(boolean enableOCR) {
        this.enableOCR = enableOCR;
    }

    public String getOcrLanguage() {
        return ocrLanguage;
    }

    public void setOcrLanguage(String ocrLanguage) {
        this.ocrLanguage = ocrLanguage;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public long getProcessTimeout() {
        return processTimeout;
    }

    public void setProcessTimeout(long processTimeout) {
        this.processTimeout = processTimeout;
    }

    public boolean isIncludeLayoutInfo() {
        return includeLayoutInfo;
    }

    public void setIncludeLayoutInfo(boolean includeLayoutInfo) {
        this.includeLayoutInfo = includeLayoutInfo;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public boolean isContentInBody() {
        return contentInBody;
    }

    public void setContentInBody(boolean contentInBody) {
        this.contentInBody = contentInBody;
    }

    public DoclingConfiguration copy() {
        try {
            return (DoclingConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
