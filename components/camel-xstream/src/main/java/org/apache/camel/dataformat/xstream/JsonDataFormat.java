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
package org.apache.camel.dataformat.xstream;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import javax.xml.stream.XMLStreamException;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.json.AbstractJsonWriter;
import com.thoughtworks.xstream.io.json.JsonWriter;
import com.thoughtworks.xstream.io.xml.QNameMap;
import com.thoughtworks.xstream.io.xml.StaxReader;
import com.thoughtworks.xstream.io.xml.StaxWriter;
import org.apache.camel.Exchange;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.codehaus.jettison.mapped.Configuration;
import org.codehaus.jettison.mapped.MappedXMLInputFactory;
import org.codehaus.jettison.mapped.MappedXMLOutputFactory;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a>
 * ({@link org.apache.camel.spi.DataFormat}) using XStream and Jettison to marshal to and from JSON
 */
@Dataformat("json-xstream")
@Metadata(includeProperties = "prettyPrint,dropRootNode,contentTypeHeader")
public class JsonDataFormat extends AbstractXStreamWrapper {
    private MappedXMLOutputFactory mof;
    private MappedXMLInputFactory mif;
    private boolean prettyPrint;
    private boolean dropRootNode;

    public JsonDataFormat() {
    }

    @Override
    public String getDataFormatName() {
        return "json-xstream";
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public boolean isDropRootNode() {
        return dropRootNode;
    }

    public void setDropRootNode(boolean dropRootNode) {
        this.dropRootNode = dropRootNode;
    }

    @Override
    public void marshal(Exchange exchange, Object body, OutputStream stream) throws Exception {
        super.marshal(exchange, body, stream);

        if (isContentTypeHeader()) {
            if (exchange.hasOut()) {
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/json");
            } else {
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
            }
        }
    }

    @Override
    protected XStream createXStream(ClassResolver resolver, ClassLoader classLoader) {
        XStream xs = super.createXStream(resolver, classLoader);
        if (getMode() != null) {
            xs.setMode(getModeFromString(getMode()));
        } else {
            xs.setMode(XStream.NO_REFERENCES);
        }
        return xs;
    }

    @Override
    protected HierarchicalStreamWriter createHierarchicalStreamWriter(Exchange exchange, Object body, OutputStream stream) throws XMLStreamException {
        if (isPrettyPrint()) {
            // the json spec. expects UTF-8 as the default encoding
            if (isDropRootNode()) {
                return new JsonWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8), AbstractJsonWriter.DROP_ROOT_MODE);
            } else {
                return new JsonWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
            }
        }

        return new StaxWriter(new QNameMap(), mof.createXMLStreamWriter(stream));
    }

    @Override
    protected HierarchicalStreamReader createHierarchicalStreamReader(Exchange exchange, InputStream stream) throws XMLStreamException {
        return new StaxReader(new QNameMap(), mif.createXMLStreamReader(stream));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        Configuration config = new Configuration();
        config.setDropRootElement(dropRootNode);

        mof = new MappedXMLOutputFactory(config);
        mif = new MappedXMLInputFactory(config);
    }
}
