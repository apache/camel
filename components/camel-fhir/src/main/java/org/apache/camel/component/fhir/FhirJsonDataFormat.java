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
package org.apache.camel.component.fhir;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.hl7.fhir.instance.model.api.IBaseResource;

public class FhirJsonDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private FhirContext fhirContext;
    private String fhirVersion;
    private boolean contentTypeHeader = true;

    public FhirContext getFhirContext() {
        return fhirContext;
    }

    public void setFhirContext(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    public String getFhirVersion() {
        return fhirVersion;
    }

    public void setFhirVersion(String fhirVersion) {
        this.fhirVersion = fhirVersion;
    }

    public boolean isContentTypeHeader() {
        return contentTypeHeader;
    }

    public void setContentTypeHeader(boolean contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    @Override
    public void marshal(Exchange exchange, Object o, OutputStream outputStream) throws Exception {
        IBaseResource iBaseResource;
        if (!(o instanceof IBaseResource)) {
            iBaseResource = exchange.getContext().getTypeConverter().mandatoryConvertTo(IBaseResource.class, exchange, o);
        } else {
            iBaseResource = (IBaseResource) o;
        }

        fhirContext.newJsonParser().encodeResourceToWriter(iBaseResource, new OutputStreamWriter(outputStream));

        if (isContentTypeHeader()) {
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        return fhirContext.newJsonParser().parseResource(new InputStreamReader(inputStream));
    }

    @Override
    public String getDataFormatName() {
        return "fhirJson";
    }

    @Override
    protected void doStart() throws Exception {
        if (fhirContext == null && fhirVersion != null) {
            FhirVersionEnum version = FhirVersionEnum.valueOf(fhirVersion);
            fhirContext = new FhirContext(version);
        } else if (fhirContext == null) {
            fhirContext = FhirContext.forDstu3();
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
