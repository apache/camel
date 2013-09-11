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
package org.apache.camel.model.dataformat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;

/**
 * Represents a <a href="http://camel.apache.org/xmljson.html">XML-JSON</a> {@link org.apache.camel.spi.DataFormat}.
 *
 * @version 
 */
@XmlRootElement(name = "xmljson")
@XmlAccessorType(XmlAccessType.FIELD)
public class XmlJsonDataFormat extends DataFormatDefinition {
    
    public static final String TYPE_HINTS = "typeHints";
    public static final String REMOVE_NAMESPACE_PREFIXES = "removeNamespacePrefixes";
    public static final String SKIP_NAMESPACES = "skipNamespaces";
    public static final String TRIM_SPACES = "trimSpaces";
    public static final String SKIP_WHITESPACE = "skipWhitespace";
    public static final String EXPANDABLE_PROPERTIES = "expandableProperties";
    public static final String ARRAY_NAME = "arrayName";
    public static final String ELEMENT_NAME = "elementName";
    public static final String ROOT_NAME = "rootName";
    public static final String NAMESPACE_LENIENT = "namespaceLenient";
    public static final String FORCE_TOP_LEVEL_OBJECT = "forceTopLevelObject";
    public static final String ENCODING = "encoding";
    
    @XmlAttribute
    private String encoding;
    @XmlAttribute
    private String elementName;
    @XmlAttribute
    private String arrayName;
    @XmlAttribute
    private Boolean forceTopLevelObject;
    @XmlAttribute
    private Boolean namespaceLenient;
    @XmlAttribute
    private String rootName;
    @XmlAttribute
    private Boolean skipWhitespace;
    @XmlAttribute
    private Boolean trimSpaces;
    @XmlAttribute
    private Boolean skipNamespaces;
    @XmlAttribute
    private Boolean removeNamespacePrefixes;
    @XmlAttribute @XmlList
    private List<String> expandableProperties;
    @XmlAttribute
    private String typeHints;

    public XmlJsonDataFormat() {
        super("xmljson");
    }

    public XmlJsonDataFormat(Map<String, String> options) {
        super("xmljson");
        if (options.containsKey(ENCODING)) {
            encoding = options.get(ENCODING);
        }
        if (options.containsKey(FORCE_TOP_LEVEL_OBJECT)) {
            forceTopLevelObject = Boolean.parseBoolean(options.get(FORCE_TOP_LEVEL_OBJECT));
        }
        if (options.containsKey(NAMESPACE_LENIENT)) {
            namespaceLenient = Boolean.parseBoolean(options.get(NAMESPACE_LENIENT));
        }
        if (options.containsKey(ROOT_NAME)) {
            rootName = options.get(ROOT_NAME);
        }
        if (options.containsKey(ELEMENT_NAME)) {
            encoding = options.get(ELEMENT_NAME);
        }
        if (options.containsKey(ARRAY_NAME)) {
            elementName = options.get(ARRAY_NAME);
        }
        if (options.containsKey(EXPANDABLE_PROPERTIES)) {
            expandableProperties = Arrays.asList(options.get(EXPANDABLE_PROPERTIES).split(" "));
        }
        if (options.containsKey(SKIP_WHITESPACE)) {
            skipWhitespace = Boolean.parseBoolean(options.get(SKIP_WHITESPACE));
        }
        if (options.containsKey(TRIM_SPACES)) {
            trimSpaces = Boolean.parseBoolean(options.get(TRIM_SPACES));
        }
        if (options.containsKey(SKIP_NAMESPACES)) {
            skipNamespaces = Boolean.parseBoolean(options.get(SKIP_NAMESPACES));
        }
        if (options.containsKey(REMOVE_NAMESPACE_PREFIXES)) {
            removeNamespacePrefixes = Boolean.parseBoolean(options.get(REMOVE_NAMESPACE_PREFIXES));
        }
        if (options.containsKey(TYPE_HINTS)) {
            typeHints = options.get(TYPE_HINTS);
        }
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (encoding != null) {
            setProperty(camelContext, dataFormat, ENCODING, encoding);
        }

        if (forceTopLevelObject != null) {
            setProperty(camelContext, dataFormat, FORCE_TOP_LEVEL_OBJECT, forceTopLevelObject);
        }

        if (namespaceLenient != null) {
            setProperty(camelContext, dataFormat, NAMESPACE_LENIENT, namespaceLenient);
        }

        if (rootName != null) {
            setProperty(camelContext, dataFormat, ROOT_NAME, rootName);
        }
        
        if (elementName != null) {
            setProperty(camelContext, dataFormat, ELEMENT_NAME, elementName);
        }

        if (arrayName != null) {
            setProperty(camelContext, dataFormat, ARRAY_NAME, arrayName);
        }

        if (expandableProperties != null && expandableProperties.size() != 0) {
            setProperty(camelContext, dataFormat, EXPANDABLE_PROPERTIES, expandableProperties);
        }

        if (skipWhitespace != null) {
            setProperty(camelContext, dataFormat, SKIP_WHITESPACE, skipWhitespace);
        }

        if (trimSpaces != null) {
            setProperty(camelContext, dataFormat, TRIM_SPACES, trimSpaces);
        }

        if (skipNamespaces != null) {
            setProperty(camelContext, dataFormat, SKIP_NAMESPACES, skipNamespaces);
        }

        if (removeNamespacePrefixes != null) {
            setProperty(camelContext, dataFormat, REMOVE_NAMESPACE_PREFIXES, removeNamespacePrefixes);
        }

        // will end up calling the setTypeHints(String s) which does the parsing from the Enum String key to the Enum value
        if (typeHints != null) {
            setProperty(camelContext, typeHints, TYPE_HINTS, typeHints);
        }

        //TODO: xmljson: element-namespace mapping is not implemented in the XML DSL
        // depending on adoption rate of this data format, we'll make this data format NamespaceAware so that it gets
        // the prefix-namespaceURI mappings from the context, and with a new attribute called "namespacedElements",
        // we'll associate named elements with prefixes following a format "element1:prefix1,element2:prefix2,..."
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getElementName() {
        return elementName;
    }

    public void setElementName(String elementName) {
        this.elementName = elementName;
    }

    public String getArrayName() {
        return arrayName;
    }

    public void setArrayName(String arrayName) {
        this.arrayName = arrayName;
    }

    public Boolean getForceTopLevelObject() {
        return forceTopLevelObject;
    }

    public void setForceTopLevelObject(Boolean forceTopLevelObject) {
        this.forceTopLevelObject = forceTopLevelObject;
    }

    public Boolean getNamespaceLenient() {
        return namespaceLenient;
    }

    public void setNamespaceLenient(Boolean namespaceLenient) {
        this.namespaceLenient = namespaceLenient;
    }

    public String getRootName() {
        return rootName;
    }

    public void setRootName(String rootName) {
        this.rootName = rootName;
    }

    public Boolean getSkipWhitespace() {
        return skipWhitespace;
    }

    public void setSkipWhitespace(Boolean skipWhitespace) {
        this.skipWhitespace = skipWhitespace;
    }

    public Boolean getTrimSpaces() {
        return trimSpaces;
    }

    public void setTrimSpaces(Boolean trimSpaces) {
        this.trimSpaces = trimSpaces;
    }

    public Boolean getSkipNamespaces() {
        return skipNamespaces;
    }

    public void setSkipNamespaces(Boolean skipNamespaces) {
        this.skipNamespaces = skipNamespaces;
    }

    public Boolean getRemoveNamespacePrefixes() {
        return removeNamespacePrefixes;
    }

    public void setRemoveNamespacePrefixes(Boolean removeNamespacePrefixes) {
        this.removeNamespacePrefixes = removeNamespacePrefixes;
    }

    public List<String> getExpandableProperties() {
        return expandableProperties;
    }

    public void setExpandableProperties(List<String> expandableProperties) {
        this.expandableProperties = expandableProperties;
    }

    public String getTypeHints() {
        return typeHints;
    }

    public void setTypeHints(String typeHints) {
        this.typeHints = typeHints;
    }

}