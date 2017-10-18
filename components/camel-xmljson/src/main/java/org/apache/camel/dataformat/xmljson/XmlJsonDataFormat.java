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
package org.apache.camel.dataformat.xmljson;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.json.JSON;
import net.sf.json.JSONSerializer;
import net.sf.json.xml.XMLSerializer;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> ({@link DataFormat}) using 
 * <a href="http://json-lib.sourceforge.net/">json-lib</a> to convert between XML
 * and JSON directly.
 */
public class XmlJsonDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private XMLSerializer serializer;

    private String encoding;
    private String elementName;
    private String arrayName;
    private Boolean forceTopLevelObject;
    private Boolean namespaceLenient;
    private List<NamespacesPerElementMapping> namespaceMappings;
    private String rootName;
    private Boolean skipWhitespace;
    private Boolean trimSpaces;
    private Boolean skipNamespaces;
    private Boolean removeNamespacePrefixes;
    private List<String> expandableProperties;
    private TypeHintsEnum typeHints;
    private boolean contentTypeHeader = true;

    public XmlJsonDataFormat() {
    }

    @Override
    public String getDataFormatName() {
        return "xmljson";
    }

    @Override
    protected void doStart() throws Exception {
        serializer = new XMLSerializer();

        if (forceTopLevelObject != null) {
            serializer.setForceTopLevelObject(forceTopLevelObject);
        }

        if (namespaceLenient != null) {
            serializer.setNamespaceLenient(namespaceLenient);
        }

        if (namespaceMappings != null) {
            for (NamespacesPerElementMapping nsMapping : namespaceMappings) {
                for (Entry<String, String> entry : nsMapping.namespaces.entrySet()) {
                    // prefix, URI, elementName (which can be null or empty
                    // string, in which case the
                    // mapping is added to the root element
                    serializer.addNamespace(entry.getKey(), entry.getValue(), nsMapping.element);
                }
            }
        }

        if (rootName != null) {
            serializer.setRootName(rootName);
        }

        if (elementName != null) {
            serializer.setElementName(elementName);
        }

        if (arrayName != null) {
            serializer.setArrayName(arrayName);
        }

        if (expandableProperties != null && expandableProperties.size() != 0) {
            serializer.setExpandableProperties(expandableProperties.toArray(new String[expandableProperties.size()]));
        }

        if (skipWhitespace != null) {
            serializer.setSkipWhitespace(skipWhitespace);
        }

        if (trimSpaces != null) {
            serializer.setTrimSpaces(trimSpaces);
        }

        if (skipNamespaces != null) {
            serializer.setSkipNamespaces(skipNamespaces);
        }

        if (removeNamespacePrefixes != null) {
            serializer.setRemoveNamespacePrefixFromElements(removeNamespacePrefixes);
        }

        if (typeHints == TypeHintsEnum.YES || typeHints == TypeHintsEnum.WITH_PREFIX) {
            serializer.setTypeHintsEnabled(true);
            if (typeHints == TypeHintsEnum.WITH_PREFIX) {
                serializer.setTypeHintsCompatibility(false);
            } else {
                serializer.setTypeHintsCompatibility(true);
            }
        } else {
            serializer.setTypeHintsEnabled(false);
            serializer.setTypeHintsCompatibility(false);
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    /**
     * Marshal from XML to JSON
     */
    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        boolean streamTreatment = true;
        // try to process as an InputStream if it's not a String
        Object xml = graph instanceof String ? null : exchange.getContext().getTypeConverter().convertTo(InputStream.class, exchange, graph);
        // if conversion to InputStream was unfeasible, fall back to String
        if (xml == null) {
            xml = exchange.getContext().getTypeConverter().mandatoryConvertTo(String.class, exchange, graph);
            streamTreatment = false;
        }

        JSON json;
        // perform the marshaling to JSON
        if (streamTreatment) {
            json = serializer.readFromStream((InputStream) xml);
        } else {
            json = serializer.read((String) xml);
        }
        // don't return the default setting here
        String encoding = IOHelper.getCharsetName(exchange, false);
        if (encoding == null) {
            encoding = getEncoding();
        }
        OutputStreamWriter osw = null;
        if (encoding != null) {
            osw = new OutputStreamWriter(stream, encoding);
        } else {
            osw = new OutputStreamWriter(stream);
        }
        json.write(osw);
        osw.flush();

        if (contentTypeHeader) {
            if (exchange.hasOut()) {
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/json");
            } else {
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
            }
        }
    }

    /**
     * Convert from JSON to XML
     */
    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        Object inBody = exchange.getIn().getBody();
        JSON toConvert;
        
        if (inBody == null) {
            return null;
        }
        
        if (inBody instanceof StreamCache) {
            long length = ((StreamCache) inBody).length();
            if (length <= 0) {
                return inBody;
            }
        }
        // if the incoming object is already a JSON object, process as-is,
        // otherwise parse it as a String
        if (inBody instanceof JSON) {
            toConvert = (JSON) inBody;
        } else {
            String jsonString = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, inBody);
            if (ObjectHelper.isEmpty(jsonString)) {
                return null;
            }
            toConvert = JSONSerializer.toJSON(jsonString);
        }

        Object answer = convertToXMLUsingEncoding(toConvert);

        if (contentTypeHeader) {
            if (exchange.hasOut()) {
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/xml");
            } else {
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/xml");
            }
        }

        return answer;
    }

    private String convertToXMLUsingEncoding(JSON json) {
        if (encoding == null) {
            return serializer.write(json);
        } else {
            return serializer.write(json, encoding);
        }
    }

    // Properties
    // -------------------------------------------------------------------------

    public XMLSerializer getSerializer() {
        return serializer;
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * Sets the encoding for the call to {@link XMLSerializer#write(JSON, String)}
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public Boolean getForceTopLevelObject() {
        return forceTopLevelObject;
    }

    /**
     * See {@link XMLSerializer#setForceTopLevelObject(boolean)}
     */
    public void setForceTopLevelObject(Boolean forceTopLevelObject) {
        this.forceTopLevelObject = forceTopLevelObject;
    }

    public Boolean getNamespaceLenient() {
        return namespaceLenient;
    }

    /**
     * See {@link XMLSerializer#setNamespaceLenient(boolean)}
     */
    public void setNamespaceLenient(Boolean namespaceLenient) {
        this.namespaceLenient = namespaceLenient;
    }

    public List<NamespacesPerElementMapping> getNamespaceMappings() {
        return namespaceMappings;
    }

    /**
     * Sets associations between elements and namespace mappings. Will only be used when converting from JSON to XML.
     * For every association, the whenever a JSON element is found that matches {@link NamespacesPerElementMapping#element},
     * the namespaces declarations specified by {@link NamespacesPerElementMapping#namespaces} will be output.
     * @see {@link XMLSerializer#addNamespace(String, String, String)}
     */
    public void setNamespaceMappings(List<NamespacesPerElementMapping> namespaceMappings) {
        this.namespaceMappings = namespaceMappings;
    }

    public String getRootName() {
        return rootName;
    }

    /**
     * See {@link XMLSerializer#setRootName(String)}
     */
    public void setRootName(String rootName) {
        this.rootName = rootName;
    }

    public Boolean getSkipWhitespace() {
        return skipWhitespace;
    }

    /**
     * See {@link XMLSerializer#setSkipWhitespace(boolean)}
     */
    public void setSkipWhitespace(Boolean skipWhitespace) {
        this.skipWhitespace = skipWhitespace;
    }

    public Boolean getTrimSpaces() {
        return trimSpaces;
    }
    
    /**
     * See {@link XMLSerializer#setTrimSpaces(boolean)}
     */
    public void setTrimSpaces(Boolean trimSpaces) {
        this.trimSpaces = trimSpaces;
    }

    public TypeHintsEnum getTypeHints() {
        return typeHints;
    }
    
    /**
     * See {@link XMLSerializer#setTypeHintsEnabled(boolean)} and {@link XMLSerializer#setTypeHintsCompatibility(boolean)}
     * @param typeHints a key in the {@link TypeHintsEnum} enumeration
     */
    public void setTypeHints(String typeHints) {
        this.typeHints = TypeHintsEnum.valueOf(typeHints);
    }

    public Boolean getSkipNamespaces() {
        return skipNamespaces;
    }

    /**
     * See {@link XMLSerializer#setSkipNamespaces(boolean)}
     */
    public void setSkipNamespaces(Boolean skipNamespaces) {
        this.skipNamespaces = skipNamespaces;
    }

    /**
     * See {@link XMLSerializer#setElementName(String)}
     */
    public void setElementName(String elementName) {
        this.elementName = elementName;
    }

    public String getElementName() {
        return elementName;
    }

    /**
     * See {@link XMLSerializer#setArrayName(String)}
     */
    public void setArrayName(String arrayName) {
        this.arrayName = arrayName;
    }

    public String getArrayName() {
        return arrayName;
    }


    public boolean isContentTypeHeader() {
        return contentTypeHeader;
    }

    /**
     * If enabled then XmlJson will set the Content-Type header to <tt>application/json</tt> when marshalling,
     * and <tt>application/xml</tt> when unmarshalling.
     */
    public void setContentTypeHeader(boolean contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    /**
     * See {@link XMLSerializer#setExpandableProperties(String[])}
     */
    public void setExpandableProperties(List<String> expandableProperties) {
        this.expandableProperties = expandableProperties;
    }

    public List<String> getExpandableProperties() {
        return expandableProperties;
    }

    /**
     * See {@link XMLSerializer#setRemoveNamespacePrefixFromElements(boolean)}
     */
    public void setRemoveNamespacePrefixes(Boolean removeNamespacePrefixes) {
        this.removeNamespacePrefixes = removeNamespacePrefixes;
    }

    public Boolean getRemoveNamespacePrefixes() {
        return removeNamespacePrefixes;
    }

    /**
     * Encapsulates the information needed to bind namespace declarations to XML elements when performing JSON to XML conversions
     * Given the following JSON: { "root:": { "element": "value", "element2": "value2" }}, it will produce the following XML when "element" is
     * bound to prefix "ns1" and namespace URI "http://mynamespace.org":
     * <root><element xmlns:ns1="http://mynamespace.org">value</element><element2>value2</element2></root>
     * For convenience, the {@link NamespacesPerElementMapping#NamespacesPerElementMapping(String, String)} constructor allows to specify
     * multiple prefix-namespaceURI pairs in just one String line, the format being: |ns1|http://mynamespace.org|ns2|http://mynamespace2.org|
     *
     */
    public static class NamespacesPerElementMapping {
        public String element;
        public Map<String, String> namespaces;

        public NamespacesPerElementMapping(String element, Map<String, String> namespaces) {
            this.element = element;
            this.namespaces = namespaces;
        }

        public NamespacesPerElementMapping(String element, String prefix, String uri) {
            this.element = element;
            this.namespaces = new HashMap<String, String>();
            this.namespaces.put(prefix, uri);
        }

        public NamespacesPerElementMapping(String element, String pipeSeparatedMappings) {
            this.element = element;
            this.namespaces = new HashMap<String, String>();
            String[] origTokens = pipeSeparatedMappings.split("\\|");
            // drop the first token
            String[] tokens = Arrays.copyOfRange(origTokens, 1, origTokens.length);

            if (tokens.length % 2 != 0) {
                throw new IllegalArgumentException("Even number of prefix-namespace tokens is expected, number of tokens parsed: " + tokens.length);
            }
            int i = 0;
            // |ns1|http://test.org|ns2|http://test2.org|
            while (i < (tokens.length - 1)) {
                this.namespaces.put(tokens[i], tokens[++i]);
                i++;
            }
        }

    }

}
