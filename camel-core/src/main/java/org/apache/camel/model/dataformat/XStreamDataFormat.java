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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.ObjectHelper;

/**
 * XSTream data format is used for unmarshal a XML payload to POJO or to marshal POJO back to XML payload.
 *
 * @version 
 */
@Metadata(firstVersion = "1.3.0", label = "dataformat,transformation,xml,json", title = "XStream")
@XmlRootElement(name = "xstream")
@XmlAccessorType(XmlAccessType.NONE)
public class XStreamDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private String permissions;
    @XmlAttribute
    private String encoding;
    @XmlAttribute
    private String driver;
    @XmlAttribute
    private String driverRef;
    @XmlAttribute
    private String mode;

    @XmlJavaTypeAdapter(ConvertersAdapter.class)
    @XmlElement(name = "converters")
    private List<String> converters;
    @XmlJavaTypeAdapter(AliasAdapter.class)
    @XmlElement(name = "aliases")
    private Map<String, String> aliases;
    @XmlJavaTypeAdapter(OmitFieldsAdapter.class)
    @XmlElement(name = "omitFields")
    private Map<String, String[]> omitFields;
    @XmlJavaTypeAdapter(ImplicitCollectionsAdapter.class)
    @XmlElement(name = "implicitCollections")
    private Map<String, String[]> implicitCollections;

    public XStreamDataFormat() {
        super("xstream");
    }
    
    public XStreamDataFormat(String encoding) {
        this();
        setEncoding(encoding);
    }
    
    public String getEncoding() {
        return encoding;
    }

    /**
     * Sets the encoding to use
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getDriver() {
        return driver;
    }

    /**
     * To use a custom XStream driver.
     * The instance must be of type com.thoughtworks.xstream.io.HierarchicalStreamDriver
     */
    public void setDriver(String driver) {
        this.driver = driver;
    }

    public String getDriverRef() {
        return driverRef;
    }

    /**
     * To refer to a custom XStream driver to lookup in the registry.
     * The instance must be of type com.thoughtworks.xstream.io.HierarchicalStreamDriver
     */
    public void setDriverRef(String driverRef) {
        this.driverRef = driverRef;
    }
    
    public String getMode() {
        return mode;
    }

    /**
     * Mode for dealing with duplicate references The possible values are:
     * <ul>
     *     <li>NO_REFERENCES</li>
     *     <li>ID_REFERENCES</li>
     *     <li>XPATH_RELATIVE_REFERENCES</li>
     *     <li>XPATH_ABSOLUTE_REFERENCES</li>
     *     <li>SINGLE_NODE_XPATH_RELATIVE_REFERENCES</li>
     *     <li>SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES</li>
     * </ul>
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<String> getConverters() {
        return converters;
    }

    /**
     * List of class names for using custom XStream converters.
     * The classes must be of type com.thoughtworks.xstream.converters.Converter
     */
    public void setConverters(List<String> converters) {
        this.converters = converters;
    }

    public Map<String, String> getAliases() {
        return aliases;
    }

    /**
     * Alias a Class to a shorter name to be used in XML elements.
     */
    public void setAliases(Map<String, String> aliases) {
        this.aliases = aliases;
    }

    public Map<String, String[]> getOmitFields() {
        return omitFields;
    }

    /**
     * Prevents a field from being serialized. To omit a field you must always provide the
     * declaring type and not necessarily the type that is converted.
     */
    public void setOmitFields(Map<String, String[]> omitFields) {
        this.omitFields = omitFields;
    }

    public Map<String, String[]> getImplicitCollections() {
        return implicitCollections;
    }

    /**
     * Adds a default implicit collection which is used for any unmapped XML tag.
     */
    public void setImplicitCollections(Map<String, String[]> implicitCollections) {
        this.implicitCollections = implicitCollections;
    }

    public String getPermissions() {
        return permissions;
    }

    /**
     * Adds permissions that controls which Java packages and classes XStream is allowed to use during
     * unmarshal from xml/json to Java beans.
     * <p/>
     * A permission must be configured either here or globally using a JVM system property. The permission
     * can be specified in a syntax where a plus sign is allow, and minus sign is deny.
     * <br/>
     * Wildcards is supported by using <tt>.*</tt> as prefix. For example to allow <tt>com.foo</tt> and all subpackages
     * then specfy <tt>+com.foo.*</tt>. Multiple permissions can be configured separated by comma, such as
     * <tt>+com.foo.*,-com.foo.bar.MySecretBean</tt>.
     * <br/>
     * The following default permission is always included: <tt>"-*,java.lang.*,java.util.*"</tt> unless
     * its overridden by specifying a JVM system property with they key <tt>org.apache.camel.xstream.permissions</tt>.
     */
    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    /**
     * To add permission for the given pojo classes.
     * @param type the pojo class(es) xstream should use as allowed permission
     * @see #setPermissions(String)
     */
    public void setPermissions(Class<?>... type) {
        CollectionStringBuffer csb = new CollectionStringBuffer(",");
        for (Class<?> clazz : type) {
            csb.append("+");
            csb.append(clazz.getName());
        }
        setPermissions(csb.toString());
    }

    @Override
    protected DataFormat createDataFormat(RouteContext routeContext) {
        if ("json".equals(this.driver)) {
            setProperty(routeContext.getCamelContext(), this, "dataFormatName", "json-xstream");
        }
        DataFormat answer = super.createDataFormat(routeContext);
        // need to lookup the reference for the xstreamDriver
        if (ObjectHelper.isNotEmpty(driverRef)) {
            setProperty(routeContext.getCamelContext(), answer, "xstreamDriver", CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), driverRef));
        }
        return answer;
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (this.permissions != null) {
            setProperty(camelContext, dataFormat, "permissions", this.permissions);
        }
        if (encoding != null) {
            setProperty(camelContext, dataFormat, "encoding", encoding);
        }
        if (this.converters != null) {
            setProperty(camelContext, dataFormat, "converters", this.converters);
        }
        if (this.aliases != null) {
            setProperty(camelContext, dataFormat, "aliases", this.aliases);
        }
        if (this.omitFields != null) {
            setProperty(camelContext, dataFormat, "omitFields", this.omitFields);
        }
        if (this.implicitCollections != null) {
            setProperty(camelContext, dataFormat, "implicitCollections", this.implicitCollections);
        }
        if (this.mode != null) {
            setProperty(camelContext, dataFormat, "mode", mode);
        }
    }
    
    

    @XmlTransient
    public static class ConvertersAdapter extends XmlAdapter<ConverterList, List<String>> {
        @Override
        public ConverterList marshal(List<String> v) throws Exception {
            if (v == null) {
                return null;
            }

            List<ConverterEntry> list = new ArrayList<ConverterEntry>();
            for (String str : v) {
                ConverterEntry entry = new ConverterEntry();
                entry.setClsName(str);
                list.add(entry);
            }
            ConverterList converterList = new ConverterList();
            converterList.setList(list);
            return converterList;
        }

        @Override
        public List<String> unmarshal(ConverterList v) throws Exception {
            if (v == null) {
                return null;
            }

            List<String> list = new ArrayList<String>();
            for (ConverterEntry entry : v.getList()) {
                list.add(entry.getClsName());
            }
            return list;
        }
    }

    @XmlAccessorType(XmlAccessType.NONE)
    @XmlType(name = "converterList", namespace = "http://camel.apache.org/schema/spring")
    public static class ConverterList {
        @XmlElement(name = "converter", namespace = "http://camel.apache.org/schema/spring")
        private List<ConverterEntry> list;

        public List<ConverterEntry> getList() {
            return list;
        }

        public void setList(List<ConverterEntry> list) {
            this.list = list;
        }
    }

    @XmlAccessorType(XmlAccessType.NONE)
    @XmlType(name = "converterEntry", namespace = "http://camel.apache.org/schema/spring")
    public static class ConverterEntry {
        @XmlAttribute(name = "class")
        private String clsName;

        public String getClsName() {
            return clsName;
        }

        public void setClsName(String clsName) {
            this.clsName = clsName;
        }
    }

    @XmlTransient
    public static class ImplicitCollectionsAdapter 
            extends XmlAdapter<ImplicitCollectionList, Map<String, String[]>> {

        @Override
        public ImplicitCollectionList marshal(Map<String, String[]> v) throws Exception {
            if (v == null || v.isEmpty()) {
                return null;
            }

            List<ImplicitCollectionEntry> list = new ArrayList<ImplicitCollectionEntry>();
            for (Entry<String, String[]> e : v.entrySet()) {
                ImplicitCollectionEntry entry = new ImplicitCollectionEntry(e.getKey(), e.getValue());
                list.add(entry);
            }

            ImplicitCollectionList collectionList = new ImplicitCollectionList();
            collectionList.setList(list);

            return collectionList;
        }

        @Override
        public Map<String, String[]> unmarshal(ImplicitCollectionList v) throws Exception {
            if (v == null) {
                return null;
            }

            Map<String, String[]> map = new HashMap<String, String[]>();
            for (ImplicitCollectionEntry entry : v.getList()) {
                map.put(entry.getClsName(), entry.getFields());
            }
            return map;
        }
    }

    @XmlAccessorType(XmlAccessType.NONE)
    @XmlType(name = "implicitCollectionList", namespace = "http://camel.apache.org/schema/spring")
    public static class ImplicitCollectionList {
        @XmlElement(name = "class", namespace = "http://camel.apache.org/schema/spring")
        private List<ImplicitCollectionEntry> list;

        public List<ImplicitCollectionEntry> getList() {
            return list;
        }

        public void setList(List<ImplicitCollectionEntry> list) {
            this.list = list;
        }
    }

    @XmlAccessorType(XmlAccessType.NONE)
    @XmlType(name = "implicitCollectionEntry", namespace = "http://camel.apache.org/schema/spring")
    public static class ImplicitCollectionEntry {
        @XmlAttribute(name = "name")
        private String clsName;

        @XmlElement(name = "field", namespace = "http://camel.apache.org/schema/spring")
        private String[] fields;

        public ImplicitCollectionEntry() {
        }

        public ImplicitCollectionEntry(String clsName, String[] fields) {
            this.clsName = clsName;
            this.fields = fields;
        }

        public String getClsName() {
            return clsName;
        }

        public void setClsName(String clsName) {
            this.clsName = clsName;
        }

        public String[] getFields() {
            return fields;
        }

        public void setFields(String[] fields) {
            this.fields = fields;
        }

        @Override
        public String toString() {
            return "Alias[ImplicitCollection=" + clsName + ", fields=" + Arrays.asList(this.fields) + "]";
        }
    }

    @XmlTransient
    public static class AliasAdapter extends XmlAdapter<AliasList, Map<String, String>> {

        @Override
        public AliasList marshal(Map<String, String> value) throws Exception {
            if (value == null || value.isEmpty()) {
                return null;
            }

            List<AliasEntry> ret = new ArrayList<AliasEntry>(value.size());
            for (Map.Entry<String, String> entry : value.entrySet()) {
                ret.add(new AliasEntry(entry.getKey(), entry.getValue()));
            }
            AliasList jaxbMap = new AliasList();
            jaxbMap.setList(ret);
            return jaxbMap;
        }

        @Override
        public Map<String, String> unmarshal(AliasList value) throws Exception {
            if (value == null || value.getList() == null || value.getList().isEmpty()) {
                return null;
            }

            Map<String, String> answer = new HashMap<String, String>();
            for (AliasEntry alias : value.getList()) {
                answer.put(alias.getName(), alias.getClsName());
            }
            return answer;
        }
    }

    @XmlAccessorType(XmlAccessType.NONE)
    @XmlType(name = "aliasList", namespace = "http://camel.apache.org/schema/spring")
    public static class AliasList {
        @XmlElement(name = "alias", namespace = "http://camel.apache.org/schema/spring")
        private List<AliasEntry> list;

        public List<AliasEntry> getList() {
            return list;
        }

        public void setList(List<AliasEntry> list) {
            this.list = list;
        }
    }

    @XmlAccessorType(XmlAccessType.NONE)
    @XmlType(name = "aliasEntry", namespace = "http://camel.apache.org/schema/spring")
    public static class AliasEntry {

        @XmlAttribute
        private String name;

        @XmlAttribute(name = "class")
        private String clsName;

        public AliasEntry() {
        }

        public AliasEntry(String key, String clsName) {
            this.name = key;
            this.clsName = clsName;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getClsName() {
            return clsName;
        }

        public void setClsName(String clsName) {
            this.clsName = clsName;
        }

        @Override
        public String toString() {
            return "Alias[name=" + name + ", class=" + clsName + "]";
        }
    }

    @XmlTransient
    public static class OmitFieldsAdapter
            extends XmlAdapter<OmitFieldList, Map<String, String[]>> {

        @Override
        public OmitFieldList marshal(Map<String, String[]> v) throws Exception {
            if (v == null || v.isEmpty()) {
                return null;
            }

            List<OmitFieldEntry> list = new ArrayList<OmitFieldEntry>();
            for (Entry<String, String[]> e : v.entrySet()) {
                OmitFieldEntry entry = new OmitFieldEntry(e.getKey(), e.getValue());
                list.add(entry);
            }

            OmitFieldList collectionList = new OmitFieldList();
            collectionList.setList(list);

            return collectionList;
        }

        @Override
        public Map<String, String[]> unmarshal(OmitFieldList v) throws Exception {
            if (v == null || v.getList() == null || v.getList().isEmpty()) {
                return null;
            }

            Map<String, String[]> map = new HashMap<String, String[]>();
            for (OmitFieldEntry entry : v.getList()) {
                map.put(entry.getClsName(), entry.getFields());
            }
            return map;
        }
    }

    @XmlAccessorType(XmlAccessType.NONE)
    @XmlType(name = "omitFieldList", namespace = "http://camel.apache.org/schema/spring")
    public static class OmitFieldList {
        @XmlElement(name = "omitField", namespace = "http://camel.apache.org/schema/spring")
        private List<OmitFieldEntry> list;

        public List<OmitFieldEntry> getList() {
            return list;
        }

        public void setList(List<OmitFieldEntry> list) {
            this.list = list;
        }
    }

    @XmlAccessorType(XmlAccessType.NONE)
    @XmlType(name = "omitFieldEntry", namespace = "http://camel.apache.org/schema/spring")
    public static class OmitFieldEntry {

        @XmlAttribute(name = "class")
        private String clsName;

        @XmlElement(name = "field", namespace = "http://camel.apache.org/schema/spring")
        private String[] fields;

        public OmitFieldEntry() {
        }

        public OmitFieldEntry(String clsName, String[] fields) {
            this.clsName = clsName;
            this.fields = fields;
        }

        public String getClsName() {
            return clsName;
        }

        public void setClsName(String clsName) {
            this.clsName = clsName;
        }

        public String[] getFields() {
            return fields;
        }

        public void setFields(String[] fields) {
            this.fields = fields;
        }

        @Override
        public String toString() {
            return "OmitField[" + clsName + ", fields=" + Arrays.asList(this.fields) + "]";
        }
    }
}