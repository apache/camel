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
package org.apache.camel.model.dataformat;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * The MIME Multipart data format is used for marshalling Camel messages with attachments
 * into MIME-Multipart message, and vise-versa.
 */
@Metadata(firstVersion = "2.17.0", label = "dataformat,transformation", title = "MIME Multipart")
@XmlRootElement(name = "mime-multipart")
@XmlAccessorType(XmlAccessType.FIELD)
public class MimeMultipartDataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(defaultValue = "mixed")
    private String multipartSubType = "mixed";
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String multipartWithoutAttachment;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String headersInline;
    @XmlAttribute
    private String includeHeaders;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String binaryContent;

    public MimeMultipartDataFormat() {
        super("mime-multipart");
    }

    public String getMultipartSubType() {
        return multipartSubType;
    }

    /**
     * Specify the subtype of the MIME Multipart.
     * <p>
     * Default is "mixed".
     */
    public void setMultipartSubType(String multipartSubType) {
        this.multipartSubType = multipartSubType;
    }

    public String getMultipartWithoutAttachment() {
        return multipartWithoutAttachment;
    }

    /**
     * Defines whether a message without attachment is also marshaled into a
     * MIME Multipart (with only one body part).
     * <p>
     * Default is "false".
     */
    public void setMultipartWithoutAttachment(String multipartWithoutAttachment) {
        this.multipartWithoutAttachment = multipartWithoutAttachment;
    }

    public String getHeadersInline() {
        return headersInline;
    }

    /**
     * Defines whether the MIME-Multipart headers are part of the message body
     * (true) or are set as Camel headers (false).
     * <p>
     * Default is "false".
     */
    public void setHeadersInline(String headersInline) {
        this.headersInline = headersInline;
    }

    public String getBinaryContent() {
        return binaryContent;
    }

    /**
     * A regex that defines which Camel headers are also included as MIME
     * headers into the MIME multipart. This will only work if headersInline is
     * set to true.
     * <p>
     * Default is to include no headers
     */
    public void setIncludeHeaders(String includeHeaders) {
        this.includeHeaders = includeHeaders;
    }

    public String getIncludeHeaders() {
        return includeHeaders;
    }

    /**
     * Defines whether the content of binary parts in the MIME multipart is
     * binary (true) or Base-64 encoded (false)
     * <p>
     * Default is "false".
     */
    public void setBinaryContent(String binaryContent) {
        this.binaryContent = binaryContent;
    }
}
