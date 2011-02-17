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
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents the XStream XML {@link org.apache.camel.spi.DataFormat}
 *
 * @version 
 */
@XmlRootElement(name = "xstream")
@XmlAccessorType(XmlAccessType.NONE)
public class XStreamDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private String encoding;

    @XmlAttribute
    private String driver = "xml";

    @XmlAttribute
    private String driverRef;

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
    
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public String getDriverRef() {
        return driverRef;
    }

    public void setDriverRef(String driverRef) {
        this.driverRef = driverRef;
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

    public Map<String, String[]> getOmitFields() {
        return omitFields;
    }

    public void setOmitFields(Map<String, String[]> omitFields) {
        this.omitFields = omitFields;
    }

    public Map<String, String[]> getImplicitCollections() {
        return implicitCollections;
    }

    public void setImplicitCollections(Map<String, String[]> implicitCollections) {
        this.implicitCollections = implicitCollections;
    }

    @Override
    protected DataFormat createDataFormat(RouteContext routeContext) {
        if ("json".equals(this.driver)) {
            setProperty(this, "dataFormatName", "json-xstream");
        }
        DataFormat answer = super.createDataFormat(routeContext);
        // need to lookup the reference for the xstreamDriver
        if (ObjectHelper.isNotEmpty(driverRef)) {
            setProperty(answer, "xstreamDriver", CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), driverRef));
        }
        return answer;
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat) {
        if (encoding != null) {
            setProperty(dataFormat, "encoding", encoding);
        }
        if (this.converters != null) {
            setProperty(dataFormat, "converters", this.converters);
        }
        if (this.aliases != null) {
            setProperty(dataFormat, "aliases", this.aliases);
        }
        if (this.omitFields != null) {
            setProperty(dataFormat, "omitFields", this.omitFields);
        }
        if (this.implicitCollections != null) {
            setProperty(dataFormat, "implicitCollections", this.implicitCollections);
        }
    }

    @XmlTransient
    public static class ConvertersAdapter extends XmlAdapter<ConverterList, List<String>> {
        @Override
        public ConverterList marshal(List<String> v) throws Exception {
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
            List<String> list = new ArrayList<String>();
            for (ConverterEntry entry : v.getList()) {
                list.add(entry.getClsName());
            }
            return list;
        }
    }

    @XmlAccessorType(XmlAccessType.NONE)
    public static class ConverterList {
        @XmlElement(name = "converter")
        private List<ConverterEntry> list = new ArrayList<ConverterEntry>();

        public List<ConverterEntry> getList() {
            return list;
        }

        public void setList(List<ConverterEntry> list) {
            this.list = list;
        }
    }

    @XmlAccessorType(XmlAccessType.NONE)
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
            Map<String, String[]> map = new HashMap<String, String[]>();
            for (ImplicitCollectionEntry entry : v.getList()) {
                map.put(entry.getClsName(), entry.getFields());
            }
            return map;
        }
    }

    @XmlAccessorType(XmlAccessType.NONE)
    public static class ImplicitCollectionList {
        @XmlElement(name = "class")
        private List<ImplicitCollectionEntry> list = new ArrayList<ImplicitCollectionEntry>();

        public List<ImplicitCollectionEntry> getList() {
            return list;
        }

        public void setList(List<ImplicitCollectionEntry> list) {
            this.list = list;
        }
    }

    @XmlAccessorType(XmlAccessType.NONE)
    public static class ImplicitCollectionEntry {
        @XmlAttribute(name = "name")
        private String clsName;

        @XmlElement(name = "field")
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
                return new AliasList();
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
            Map<String, String> answer = new HashMap<String, String>();
            for (AliasEntry alias : value.getList()) {
                answer.put(alias.getName(), alias.getClsName());
            }
            return answer;
        }

    }

    @XmlAccessorType(XmlAccessType.NONE)
    public static class AliasList {
        @XmlElement(name = "alias")
        private List<AliasEntry> list = new ArrayList<AliasEntry>();

        public List<AliasEntry> getList() {
            return list;
        }

        public void setList(List<AliasEntry> list) {
            this.list = list;
        }
    }

    @XmlAccessorType(XmlAccessType.NONE)
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
            Map<String, String[]> map = new HashMap<String, String[]>();
            for (OmitFieldEntry entry : v.getList()) {
                map.put(entry.getClsName(), entry.getFields());
            }
            return map;
        }
    }

    @XmlAccessorType(XmlAccessType.NONE)
    public static class OmitFieldList {
        @XmlElement(name = "omitField")
        private List<OmitFieldEntry> list = new ArrayList<OmitFieldEntry>();

        public List<OmitFieldEntry> getList() {
            return list;
        }

        public void setList(List<OmitFieldEntry> list) {
            this.list = list;
        }
    }

    @XmlAccessorType(XmlAccessType.NONE)
    public static class OmitFieldEntry {

        @XmlAttribute(name = "class")
        private String clsName;

        @XmlElement(name = "field")
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