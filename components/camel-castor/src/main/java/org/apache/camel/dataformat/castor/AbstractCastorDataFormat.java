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
package org.apache.camel.dataformat.castor;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

import org.apache.camel.Exchange;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ObjectHelper;
import org.exolab.castor.mapping.Mapping;
import org.exolab.castor.xml.Marshaller;
import org.exolab.castor.xml.Unmarshaller;
import org.exolab.castor.xml.XMLContext;

/**
 * An abstract class which implement <a
 * href="http://camel.apache.org/data-format.html">data format</a> ({@link DataFormat})
 * interface which leverage the Castor library for XML marshaling and
 * unmarshaling
 *
 * @version 
 */
public abstract class AbstractCastorDataFormat implements DataFormat {

    /**
     * The default encoding used for stream access.
     */
    public static final String DEFAULT_ENCODING = "UTF-8";

    private String encoding = DEFAULT_ENCODING;
    private XMLContext xmlContext;
    private String mappingFile;
    private String[] classNames;
    private String[] packages;
    private boolean validation;

    private Marshaller marshaller;
    private Unmarshaller unmarshaller;

    public AbstractCastorDataFormat() {
    }

    public AbstractCastorDataFormat(XMLContext xmlContext) {
        this.xmlContext = xmlContext;
    }

    public void marshal(Exchange exchange, Object body, OutputStream outputStream) throws Exception {
        Writer writer = new OutputStreamWriter(outputStream, encoding);
        Marshaller.marshal(body, writer);
    }

    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        Reader reader = new InputStreamReader(inputStream, encoding);
        return getUnmarshaller(exchange).unmarshal(reader);
    }

    public XMLContext getXmlContext(ClassResolver resolver) throws Exception {
        if (xmlContext == null) {
            xmlContext = new XMLContext();

            if (ObjectHelper.isNotEmpty(getMappingFile())) {
                Mapping xmlMap = new Mapping();
                xmlMap.loadMapping(resolver.loadResourceAsURL(getMappingFile()));
                xmlContext.addMapping(xmlMap);
            }

            if (getPackages() != null) {
                xmlContext.addPackages(getPackages());
            }
            if (getClassNames() != null) {
                for (String name : getClassNames()) {
                    Class clazz = resolver.resolveClass(name);
                    xmlContext.addClass(clazz);
                }
            }
        }

        return xmlContext;
    }

    public Unmarshaller getUnmarshaller(Exchange exchange) throws Exception {
        if (this.unmarshaller == null) {
            this.unmarshaller = getXmlContext(exchange.getContext().getClassResolver()).createUnmarshaller();
            this.unmarshaller.setValidation(isValidation());
        }
        return this.unmarshaller;
    }

    public Marshaller getMarshaller(Exchange exchange) throws Exception {
        if (this.marshaller == null) {
            this.marshaller = getXmlContext(exchange.getContext().getClassResolver()).createMarshaller();
            this.marshaller.setValidation(isValidation());
        }
        return this.marshaller;
    }

    public void setXmlContext(XMLContext xmlContext) {
        this.xmlContext = xmlContext;
    }

    public String getMappingFile() {
        return mappingFile;
    }

    public void setMappingFile(String mappingFile) {
        this.mappingFile = mappingFile;
    }

    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    public void setUnmarshaller(Unmarshaller unmarshaller) {
        this.unmarshaller = unmarshaller;
    }

    public String[] getClassNames() {
        return classNames;
    }

    public void setClassNames(String[] classNames) {
        this.classNames = classNames;
    }

    public String[] getPackages() {
        return packages;
    }

    public void setPackages(String[] packages) {
        this.packages = packages;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public boolean isValidation() {
        return validation;
    }

    public void setValidation(boolean validation) {
        this.validation = validation;
    }
}