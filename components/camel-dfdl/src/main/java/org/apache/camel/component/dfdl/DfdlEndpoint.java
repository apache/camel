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
package org.apache.camel.component.dfdl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;

import org.w3c.dom.Document;

import org.apache.camel.Category;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ProcessorEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.daffodil.japi.Daffodil;
import org.apache.daffodil.japi.DataProcessor;
import org.apache.daffodil.japi.Diagnostic;
import org.apache.daffodil.japi.ParseResult;
import org.apache.daffodil.japi.ProcessorFactory;
import org.apache.daffodil.japi.UnparseResult;
import org.apache.daffodil.japi.infoset.W3CDOMInfosetInputter;
import org.apache.daffodil.japi.infoset.W3CDOMInfosetOutputter;
import org.apache.daffodil.japi.io.InputSourceDataInputStream;

/**
 * Transforms fixed format data such as EDI message from/to XML using a Data Format Description Language (DFDL).
 */
@UriEndpoint(firstVersion = "4.11.0", scheme = "dfdl", title = "DFDL", syntax = "dfdl:schemaUri", producerOnly = true,
             category = { Category.TRANSFORMATION })
public class DfdlEndpoint extends ProcessorEndpoint {

    @UriPath
    @Metadata(required = true, description = "The path to the DFDL schema file.")
    private String schemaUri;

    @UriParam
    @Metadata(defaultValue = "PARSE", description = "Transform direction. Either PARSE or UNPARSE")
    private ParseDirection parseDirection;

    @UriParam(description = "The root element name of the schema to use. If not specified, the first root element in the schema will be used.",
              label = "advanced", defaultValue = "")
    private String rootElement = "";

    @UriParam(description = "The root namespace of the schema to use.", label = "advanced", defaultValue = "")
    private String rootNamespace = "";

    private DataProcessor daffodilProcessor;

    public DfdlEndpoint(String uri, DfdlComponent component, String schemaFile) {
        super(uri, component);
        this.schemaUri = schemaFile;
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
            StringBuilder buf = new StringBuilder("Failed to initialize dfdl endpoint: [");
            for (Diagnostic d : processorFactory.getDiagnostics()) {
                buf.append(d.getMessage()).append("; ");
            }
            buf.append("]");
            throw new IOException(buf.toString());
        }
        this.daffodilProcessor = processorFactory.onPath("/");
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        if (getParseDirection() == ParseDirection.UNPARSE) {
            Document xmlDocument = exchange.getIn().getBody(Document.class);
            W3CDOMInfosetInputter inputter = new W3CDOMInfosetInputter(xmlDocument);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            UnparseResult result = this.daffodilProcessor.unparse(inputter, Channels.newChannel(bos));
            if (result.isError()) {
                exchange.setException(new DfdlUnparseException(result));
                return;
            }
            exchange.getMessage().setBody(bos);
        } else {
            byte[] binary = exchange.getIn().getBody(byte[].class);
            var inputStream = new InputSourceDataInputStream(binary);
            var outputter = new W3CDOMInfosetOutputter();
            ParseResult result = this.daffodilProcessor.parse(inputStream, outputter);
            if (result.isError()) {
                exchange.setException(new DfdlParseException(result));
                return;
            }
            exchange.getMessage().setBody(outputter.getResult());
        }
    }

    public ParseDirection getParseDirection() {
        return parseDirection;
    }

    public void setParseDirection(ParseDirection direction) {
        this.parseDirection = direction;
    }

    public String getSchemaUri() {
        return schemaUri;
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
