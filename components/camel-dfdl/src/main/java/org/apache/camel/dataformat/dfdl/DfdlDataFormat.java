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
package org.apache.camel.dataformat.dfdl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;

import org.w3c.dom.Document;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.component.dfdl.DfdlParseException;
import org.apache.camel.component.dfdl.DfdlUnparseException;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.daffodil.japi.Daffodil;
import org.apache.daffodil.japi.DataProcessor;
import org.apache.daffodil.japi.Diagnostic;
import org.apache.daffodil.japi.ParseResult;
import org.apache.daffodil.japi.ProcessorFactory;
import org.apache.daffodil.japi.UnparseResult;
import org.apache.daffodil.japi.infoset.W3CDOMInfosetInputter;
import org.apache.daffodil.japi.infoset.W3CDOMInfosetOutputter;
import org.apache.daffodil.japi.io.InputSourceDataInputStream;

@Dataformat("dfdl")
public class DfdlDataFormat extends ServiceSupport implements DataFormat, CamelContextAware {
    private CamelContext camelContext;
    private String schemaUri;
    private String rootElement = "";
    private String rootNamespace = "";
    private DataProcessor daffodilProcessor;

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        Document xmlDocument = camelContext.getTypeConverter().mandatoryConvertTo(Document.class, exchange, graph);
        W3CDOMInfosetInputter inputter = new W3CDOMInfosetInputter(xmlDocument);
        UnparseResult result = this.daffodilProcessor.unparse(inputter, Channels.newChannel(stream));
        if (result.isError()) {
            exchange.setException(new DfdlUnparseException(result));
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        var inputStream = new InputSourceDataInputStream(stream);
        var outputter = new W3CDOMInfosetOutputter();
        ParseResult result = this.daffodilProcessor.parse(inputStream, outputter);
        if (result.isError()) {
            exchange.setException(new DfdlParseException(result));
            return null;
        }
        return outputter.getResult();
    }

    @Override
    public void doInit() throws Exception {
        super.doInit();
        ProcessorFactory processorFactory;
        Resource schemaResource = ResourceHelper.resolveMandatoryResource(getCamelContext(), getSchemaUri());
        if (getRootElement() != null && !getRootElement().isEmpty() &&
                getRootNamespace() != null && !getRootNamespace().isEmpty()) {
            processorFactory
                    = Daffodil.compiler().compileSource(schemaResource.getURI(), getRootElement(), getRootNamespace());
        } else {
            processorFactory = Daffodil.compiler().compileSource(schemaResource.getURI());
        }
        if (processorFactory.isError()) {
            StringBuilder buf = new StringBuilder("Failed to start dfdl dataformat: [");
            for (Diagnostic d : processorFactory.getDiagnostics()) {
                buf.append(d.getMessage()).append("; ");
            }
            buf.append("]");
            throw new IOException(buf.toString());
        }
        this.daffodilProcessor = processorFactory.onPath("/");
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return this.camelContext;
    }

    public String getSchemaUri() {
        return this.schemaUri;
    }

    public void setSchemaUri(String schemaUri) {
        this.schemaUri = schemaUri;
    }

    public String getRootElement() {
        return rootElement;
    }

    public void setRootElement(String rootElement) {
        this.rootElement = rootElement;
    }

    public String getRootNamespace() {
        return rootNamespace;
    }

    public void setRootNamespace(String rootNamespace) {
        this.rootNamespace = rootNamespace;
    }
}
