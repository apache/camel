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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.CollectionStringBuffer;
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
 */
public abstract class AbstractCastorDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    /**
     * The default encoding used for stream access.
     */
    public static final String DEFAULT_ENCODING = "UTF-8";

    private CamelContext camelContext;
    private String encoding = DEFAULT_ENCODING;
    private String mappingFile;
    private String[] classNames;
    private String[] packages;
    private boolean validation;
    private volatile XMLContext xmlContext;
    private boolean contentTypeHeader = true;
    private boolean whitlistEnabled = true;
    private String allowedUnmarshallObjects;
    private String deniedUnmarshallObjects;

    public AbstractCastorDataFormat() {
    }

    public AbstractCastorDataFormat(XMLContext xmlContext) {
        this.xmlContext = xmlContext;
    }

    @Override
    public String getDataFormatName() {
        return "castor";
    }

    public void marshal(Exchange exchange, Object body, OutputStream outputStream) throws Exception {
        Writer writer = new OutputStreamWriter(outputStream, encoding);

        Marshaller marshaller = createMarshaller(exchange);
        marshaller.setWriter(writer);
        marshaller.marshal(body);

        if (contentTypeHeader) {
            if (exchange.hasOut()) {
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/xml");
            } else {
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/xml");
            }
        }
    }

    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        Reader reader = new InputStreamReader(inputStream, encoding);
        return createUnmarshaller(exchange).unmarshal(reader);
    }

    protected XMLContext createXMLContext(ClassResolver resolver, ClassLoader contextClassLoader) throws Exception {
        XMLContext xmlContext = new XMLContext();

        if (ObjectHelper.isNotEmpty(getMappingFile())) {
            Mapping xmlMap;
            if (contextClassLoader != null) {
                xmlMap = new Mapping(contextClassLoader);
            } else {
                xmlMap = new Mapping();
            }
            xmlMap.loadMapping(resolver.loadResourceAsURL(getMappingFile()));
            xmlContext.addMapping(xmlMap);
        }

        if (getPackages() != null) {
            xmlContext.addPackages(getPackages());
        }
        if (getClassNames() != null) {
            for (String name : getClassNames()) {
                Class<?> clazz = resolver.resolveClass(name);
                xmlContext.addClass(clazz);
            }
        }
        return xmlContext;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public Unmarshaller createUnmarshaller(Exchange exchange) throws Exception {
        // need to create new marshaller as we may have concurrent processing
        Unmarshaller answer = xmlContext.createUnmarshaller();
        answer.setValidation(isValidation());
        if (whitlistEnabled) {
            WhitelistObjectFactory factory = new WhitelistObjectFactory();
            factory.setAllowClasses(allowedUnmarshallObjects);
            factory.setDenyClasses(deniedUnmarshallObjects);
            answer.setObjectFactory(factory);
        }
        return answer;
    }

    public Marshaller createMarshaller(Exchange exchange) throws Exception {
        // need to create new marshaller as we may have concurrent processing
        Marshaller answer = xmlContext.createMarshaller();
        answer.setValidation(isValidation());
        return answer;
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

    public boolean isContentTypeHeader() {
        return contentTypeHeader;
    }

    /**
     * If enabled then Castor will set the Content-Type header to <tt>application/xml</tt> when marshalling.
     */
    public void setContentTypeHeader(boolean contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    public boolean isWhitlistEnabled() {
        return whitlistEnabled;
    }

    public void setWhitlistEnabled(boolean whitlistEnabled) {
        this.whitlistEnabled = whitlistEnabled;
    }

    public String getAllowedUnmarshallObjects() {
        return allowedUnmarshallObjects;
    }

    public void setAllowedUnmarshallObjects(String allowedUnmarshallObjects) {
        this.allowedUnmarshallObjects = allowedUnmarshallObjects;
    }

    public void setAllowClasses(Class... allowClasses) {
        CollectionStringBuffer csb = new CollectionStringBuffer(",");
        for (Class clazz : allowClasses) {
            csb.append(clazz.getName());
        }
        this.allowedUnmarshallObjects = csb.toString();
    }

    public String getDeniedUnmarshallObjects() {
        return deniedUnmarshallObjects;
    }

    public void setDeniedUnmarshallObjects(String deniedUnmarshallObjects) {
        this.deniedUnmarshallObjects = deniedUnmarshallObjects;
    }

    @Override
    protected void doStart() throws Exception {
        if (xmlContext == null) {
            xmlContext = createXMLContext(getCamelContext().getClassResolver(), getCamelContext().getApplicationContextClassLoader());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

}