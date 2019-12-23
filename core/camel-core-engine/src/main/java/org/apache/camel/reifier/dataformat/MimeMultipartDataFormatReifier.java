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
package org.apache.camel.reifier.dataformat;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.dataformat.MimeMultipartDataFormat;
import org.apache.camel.spi.DataFormat;

public class MimeMultipartDataFormatReifier extends DataFormatReifier<MimeMultipartDataFormat> {

    public MimeMultipartDataFormatReifier(DataFormatDefinition definition) {
        super((MimeMultipartDataFormat)definition);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (definition.getMultipartSubType() != null) {
            setProperty(camelContext, dataFormat, "multipartSubType", definition.getMultipartSubType());
        }
        if (definition.getMultipartWithoutAttachment() != null) {
            setProperty(camelContext, dataFormat, "multipartWithoutAttachment", definition.getMultipartWithoutAttachment());
        }
        if (definition.getHeadersInline() != null) {
            setProperty(camelContext, dataFormat, "headersInline", definition.getHeadersInline());
        }
        if (definition.getIncludeHeaders() != null) {
            setProperty(camelContext, dataFormat, "includeHeaders", definition.getIncludeHeaders());
        }
        if (definition.getBinaryContent() != null) {
            setProperty(camelContext, dataFormat, "binaryContent", definition.getBinaryContent());
        }
    }

}
