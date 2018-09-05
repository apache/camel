/**
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
package org.apache.camel.component.pdf;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * The pdf components provides the ability to create, modify or extract content from PDF documents.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "pdf", title = "PDF", syntax = "pdf:operation", producerOnly = true, label = "document,transformation,printing")
public class PdfEndpoint extends DefaultEndpoint {

    @UriParam
    private PdfConfiguration pdfConfiguration;

    public PdfEndpoint(String endpointUri, Component component, PdfConfiguration pdfConfiguration) {
        super(endpointUri, component);
        this.pdfConfiguration = pdfConfiguration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new PdfProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer does not supported for PDF component:" + getEndpointUri());
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public PdfConfiguration getPdfConfiguration() {
        return pdfConfiguration;
    }

    public void setPdfConfiguration(PdfConfiguration pdfConfiguration) {
        this.pdfConfiguration = pdfConfiguration;
    }
}
