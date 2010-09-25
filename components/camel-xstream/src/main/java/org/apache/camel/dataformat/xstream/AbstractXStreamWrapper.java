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
package org.apache.camel.dataformat.xstream;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.stream.XMLStreamException;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import org.apache.camel.Exchange;
import org.apache.camel.converter.jaxp.StaxConverter;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ObjectHelper;

/**
 * An abstract class which implement <a href="http://camel.apache.org/data-format.html">data format</a>
 * ({@link DataFormat}) interface which leverage the XStream library for XML or JSON's marshaling and unmarshaling
 *
 * @version $Revision$
 */
public abstract class AbstractXStreamWrapper implements DataFormat {
    
    private XStream xstream;
    private HierarchicalStreamDriver xstreamDriver;
    private StaxConverter staxConverter;
    private List<String> converters;
    private Map<String, String> aliases;
    private Map<String, String[]> omitFields;
    private Map<String, String[]> implicitCollections;

    public AbstractXStreamWrapper() {
    }
    
    public AbstractXStreamWrapper(XStream xstream) {
        this.xstream = xstream;
    }
    
    public XStream getXStream(ClassResolver resolver) {
        if (xstream == null) {
            xstream = createXStream(resolver);
        }
        return xstream;
    }

    public void setXStream(XStream xstream) {
        this.xstream = xstream;
    }

    protected XStream createXStream(ClassResolver resolver) {
        if (xstreamDriver != null) {
            xstream = new XStream(xstreamDriver);
        } else {
            xstream = new XStream();
        }

        try {
            if (this.implicitCollections != null) {
                for (Entry<String, String[]> entry : this.implicitCollections.entrySet()) {
                    for (String name : entry.getValue()) {
                        xstream.addImplicitCollection(resolver.resolveMandatoryClass(entry.getKey()), name);
                    }
                }
            }

            if (this.aliases != null) {
                for (Entry<String, String> entry : this.aliases.entrySet()) {
                    xstream.alias(entry.getKey(), resolver.resolveMandatoryClass(entry.getValue()));
                }
            }

            if (this.omitFields != null) {
                for (Entry<String, String[]> entry : this.omitFields.entrySet()) {
                    for (String name : entry.getValue()) {
                        xstream.omitField(resolver.resolveMandatoryClass(entry.getKey()), name);
                    }
                }
            }

            if (this.converters != null) {
                for (String name : this.converters) {
                    Class<Converter> converterClass = resolver.resolveMandatoryClass(name, Converter.class);
                    Converter converter;

                    Constructor con = null;
                    try {
                        con = converterClass.getDeclaredConstructor(new Class[] {XStream.class});
                    } catch (Exception e) {
                         //swallow as we null check in a moment.
                    }
                    if (con != null) {
                        converter = (Converter) con.newInstance(xstream);
                    } else {
                        converter = converterClass.newInstance();
                        try { 
                            Method method = converterClass.getMethod("setXStream", new Class[] {XStream.class});
                            if (method != null) {
                                ObjectHelper.invokeMethod(method, converter, xstream);
                            }
                        } catch (Throwable e) {
                            // swallow, as it just means the user never add an XStream setter, which is optional
                        }
                    }

                    xstream.registerConverter(converter);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to build XStream instance", e);
        }

        return xstream;
    }    

    public StaxConverter getStaxConverter() {
        if (staxConverter == null) {
            staxConverter = new StaxConverter();
        }
        return staxConverter;
    }

    public void setStaxConverter(StaxConverter staxConverter) {
        this.staxConverter = staxConverter;
    }

    public List<String> getConverters() {
        return converters;
    }

    public void setConverters(List<String> converters) {
        this.converters = converters;
    }

    public Map<String, String> getAliases() {
        return aliases;
    }

    public void setAliases(Map<String, String> aliases) {
        this.aliases = aliases;
    }

    public Map<String, String[]> getImplicitCollections() {
        return implicitCollections;
    }

    public void setImplicitCollections(Map<String, String[]> implicitCollections) {
        this.implicitCollections = implicitCollections;
    }

    public Map<String, String[]> getOmitFields() {
        return omitFields;
    }

    public void setOmitFields(Map<String, String[]> omitFields) {
        this.omitFields = omitFields;
    }

    public HierarchicalStreamDriver getXstreamDriver() {
        return xstreamDriver;
    }

    public void setXstreamDriver(HierarchicalStreamDriver xstreamDriver) {
        this.xstreamDriver = xstreamDriver;
    }

    public XStream getXstream() {
        return xstream;
    }

    public void setXstream(XStream xstream) {
        this.xstream = xstream;
    }

    public void marshal(Exchange exchange, Object body, OutputStream stream) throws Exception {
        HierarchicalStreamWriter writer = createHierarchicalStreamWriter(exchange, body, stream);
        try {
            getXStream(exchange.getContext().getClassResolver()).marshal(body, writer);
        } finally {
            writer.close();
        }
    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        HierarchicalStreamReader reader = createHierarchicalStreamReader(exchange, stream);
        try {
            return getXStream(exchange.getContext().getClassResolver()).unmarshal(reader);
        } finally {
            reader.close();
        }
    }

    protected abstract HierarchicalStreamWriter createHierarchicalStreamWriter(
            Exchange exchange, Object body, OutputStream stream) throws XMLStreamException;

    protected abstract HierarchicalStreamReader createHierarchicalStreamReader(
            Exchange exchange, InputStream stream) throws XMLStreamException;
}
