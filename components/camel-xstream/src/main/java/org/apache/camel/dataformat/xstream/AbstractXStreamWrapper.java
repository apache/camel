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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.stream.XMLStreamException;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.core.util.CompositeClassLoader;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.security.AnyTypePermission;
import com.thoughtworks.xstream.security.ExplicitTypePermission;
import com.thoughtworks.xstream.security.TypePermission;
import com.thoughtworks.xstream.security.WildcardTypePermission;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatContentTypeHeader;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.service.ServiceSupport;

/**
 * An abstract class which implement <a href="http://camel.apache.org/data-format.html">data format</a>
 * ({@link DataFormat}) interface which leverage the XStream library for XML or JSON's marshaling and unmarshaling
 */
public abstract class AbstractXStreamWrapper extends ServiceSupport implements CamelContextAware, DataFormat, DataFormatName, DataFormatContentTypeHeader {
    private static final String PERMISSIONS_PROPERTY_KEY = "org.apache.camel.xstream.permissions";

    private CamelContext camelContext;
    private XStream xstream;
    private HierarchicalStreamDriver xstreamDriver;
    private Map<String, String> converters;
    private Map<String, String> aliases;
    private Map<String, String> omitFields;
    private Map<String, String> implicitCollections;
    private String permissions;
    private String mode;
    private boolean contentTypeHeader = true;

    public AbstractXStreamWrapper() {
    }

    public AbstractXStreamWrapper(XStream xstream) {
        this.xstream = xstream;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Resolves the XStream instance to be used by this data format. If XStream is not explicitly set, new instance will
     * be created and cached.
     *
     * @param resolver class resolver to be used during a configuration of the XStream instance.
     * @return XStream instance used by this data format.
     */
    public XStream getXStream(ClassResolver resolver) {
        if (xstream == null) {
            xstream = createXStream(resolver, null);
        }
        return xstream;
    }

    /**
     * Resolves the XStream instance to be used by this data format. If XStream is not explicitly set, new instance will
     * be created and cached.
     *
     * @param context to be used during a configuration of the XStream instance
     * @return XStream instance used by this data format.
     */
    public XStream getXStream(CamelContext context) {
        if (xstream == null) {
            xstream = createXStream(context.getClassResolver(), context.getApplicationContextClassLoader());
        }
        return xstream;
    }

    public void setXStream(XStream xstream) {
        this.xstream = xstream;
    }

    protected XStream createXStream(ClassResolver resolver, ClassLoader classLoader) {
        if (xstreamDriver != null) {
            xstream = new XStream(xstreamDriver);
        } else {
            xstream = new XStream();
        }

        if (mode != null) {
            xstream.setMode(getModeFromString(mode));
        }

        ClassLoader xstreamLoader = xstream.getClassLoader();
        if (classLoader != null && xstreamLoader instanceof CompositeClassLoader) {
            ((CompositeClassLoader) xstreamLoader).add(classLoader);
        }

        try {
            if (this.implicitCollections != null) {
                for (Entry<String, String> entry : this.implicitCollections.entrySet()) {
                    String[] values = entry.getValue().split(",");
                    for (String name : values) {
                        xstream.addImplicitCollection(resolver.resolveMandatoryClass(entry.getKey()), name);
                    }
                }
            }

            if (this.aliases != null) {
                for (Entry<String, String> entry : this.aliases.entrySet()) {
                    xstream.alias(entry.getKey(), resolver.resolveMandatoryClass(entry.getValue()));
                    // It can turn the auto-detection mode off
                    xstream.processAnnotations(resolver.resolveMandatoryClass(entry.getValue()));
                }
            }

            if (this.omitFields != null) {
                for (Entry<String, String> entry : this.omitFields.entrySet()) {
                    String[] values = entry.getValue().split(",");
                    for (String name : values) {
                        xstream.omitField(resolver.resolveMandatoryClass(entry.getKey()), name);
                    }
                }
            }

            if (this.converters != null) {
                for (Entry<String, String> entry : this.converters.entrySet()) {
                    String fqn = entry.getValue();
                    Class<Converter> converterClass = resolver.resolveMandatoryClass(fqn, Converter.class);
                    Converter converter;

                    Constructor<Converter> con = null;
                    try {
                        con = converterClass.getDeclaredConstructor(new Class[]{XStream.class});
                    } catch (Exception e) {
                        //swallow as we null check in a moment.
                    }
                    if (con != null) {
                        converter = con.newInstance(xstream);
                    } else {
                        converter = converterClass.newInstance();
                        try {
                            Method method = converterClass.getMethod("setXStream", new Class[]{XStream.class});
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

            addDefaultPermissions(xstream);
            if (this.permissions != null) {
                // permissions ::= pterm (',' pterm)*   # consists of one or more terms
                // pterm       ::= aod? wterm           # each term preceded by an optional sign 
                // aod         ::= '+' | '-'            # indicates allow or deny where allow if omitted
                // wterm       ::= a class name with optional wildcard characters
                addPermissions(xstream, permissions);
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to build XStream instance", e);
        }

        return xstream;
    }

    private static void addPermissions(XStream xstream, String permissions) {
        for (String pterm : permissions.split(",")) {
            boolean aod;
            pterm = pterm.trim();
            if (pterm.startsWith("-")) {
                aod = false;
                pterm = pterm.substring(1);
            } else {
                aod = true;
                if (pterm.startsWith("+")) {
                    pterm = pterm.substring(1);
                }
            }
            TypePermission typePermission = null;
            if ("*".equals(pterm)) {
                // accept or deny any
                typePermission = AnyTypePermission.ANY;
            } else if (pterm.indexOf('*') < 0) {
                // exact type
                typePermission = new ExplicitTypePermission(new String[]{pterm});
            } else if (pterm.length() > 0) {
                // wildcard type
                typePermission = new WildcardTypePermission(new String[]{pterm});
            }
            if (typePermission != null) {
                if (aod) {
                    xstream.addPermission(typePermission);
                } else {
                    xstream.denyPermission(typePermission);
                }
            }
        }
    }

    private static void addDefaultPermissions(XStream xstream) {
        XStream.setupDefaultSecurity(xstream);

        String value = System.getProperty(PERMISSIONS_PROPERTY_KEY);
        if (value != null) {
            // using custom permissions
            addPermissions(xstream, value);
        }
    }

    protected int getModeFromString(String modeString) {
        int result;
        if ("NO_REFERENCES".equalsIgnoreCase(modeString)) {
            result = XStream.NO_REFERENCES;
        } else if ("ID_REFERENCES".equalsIgnoreCase(modeString)) {
            result = XStream.ID_REFERENCES;
        } else if ("XPATH_RELATIVE_REFERENCES".equalsIgnoreCase(modeString)) {
            result = XStream.XPATH_RELATIVE_REFERENCES;
        } else if ("XPATH_ABSOLUTE_REFERENCES".equalsIgnoreCase(modeString)) {
            result = XStream.XPATH_ABSOLUTE_REFERENCES;
        } else if ("SINGLE_NODE_XPATH_RELATIVE_REFERENCES".equalsIgnoreCase(modeString)) {
            result = XStream.SINGLE_NODE_XPATH_RELATIVE_REFERENCES;
        } else if ("SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES".equalsIgnoreCase(modeString)) {
            result = XStream.SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES;
        } else {
            throw new IllegalArgumentException("Unknown mode : " + modeString);
        }
        return result;
    }

    public Map<String, String> getConverters() {
        return converters;
    }

    public void setConverters(Map<String, String> converters) {
        this.converters = converters;
    }

    public Map<String, String> getAliases() {
        return aliases;
    }

    public void setAliases(Map<String, String> aliases) {
        this.aliases = aliases;
    }

    public Map<String, String> getOmitFields() {
        return omitFields;
    }

    public void setOmitFields(Map<String, String> omitFields) {
        this.omitFields = omitFields;
    }

    public Map<String, String> getImplicitCollections() {
        return implicitCollections;
    }

    public void setImplicitCollections(Map<String, String> implicitCollections) {
        this.implicitCollections = implicitCollections;
    }

    public HierarchicalStreamDriver getXstreamDriver() {
        return xstreamDriver;
    }

    public void setXstreamDriver(HierarchicalStreamDriver xstreamDriver) {
        this.xstreamDriver = xstreamDriver;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isContentTypeHeader() {
        return contentTypeHeader;
    }

    /**
     * If enabled then XStream will set the Content-Type header to <tt>application/json</tt> when marshalling to JSon
     * and <tt>application/xml</tt> when marshalling to XML.
     */
    public void setContentTypeHeader(boolean contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    public XStream getXstream() {
        return xstream;
    }

    public void setXstream(XStream xstream) {
        this.xstream = xstream;
    }

    @Override
    public void marshal(Exchange exchange, Object body, OutputStream stream) throws Exception {
        HierarchicalStreamWriter writer = createHierarchicalStreamWriter(exchange, body, stream);
        try {
            getXStream(exchange.getContext()).marshal(body, writer);
        } finally {
            writer.close();
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        HierarchicalStreamReader reader = createHierarchicalStreamReader(exchange, stream);
        try {
            return getXStream(exchange.getContext()).unmarshal(reader);
        } finally {
            reader.close();
        }
    }

    protected abstract HierarchicalStreamWriter createHierarchicalStreamWriter(
            Exchange exchange, Object body, OutputStream stream) throws XMLStreamException;

    protected abstract HierarchicalStreamReader createHierarchicalStreamReader(
            Exchange exchange, InputStream stream) throws XMLStreamException;

    @Override
    protected void doStart() throws Exception {
        org.apache.camel.util.ObjectHelper.notNull(camelContext, "camelContext");
        // initialize xstream
        if (xstream == null) {
            xstream = createXStream(camelContext.getClassResolver(), camelContext.getApplicationContextClassLoader());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
