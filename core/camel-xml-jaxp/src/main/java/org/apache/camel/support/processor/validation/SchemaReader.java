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
package org.apache.camel.support.processor.validation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.ls.LSResourceResolver;

import org.xml.sax.SAXException;

import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the schema used in the processor {@link ValidatingProcessor}.
 * A schema re-reading could be forced using {@link org.apache.camel.component.validator.ValidatorEndpoint#clearCachedSchema()}.
 */
public class SchemaReader {
    
    /** Key of the global option to switch either off or on  the access to external DTDs in the XML Validator for StreamSources. 
     * Only effective, if not a custom schema factory is used.*/
    public static final String ACCESS_EXTERNAL_DTD = "CamelXmlValidatorAccessExternalDTD";
    
    private static final Logger LOG = LoggerFactory.getLogger(SchemaReader.class);
    
    private String schemaLanguage = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    // must be volatile because is accessed from different threads see ValidatorEndpoint.clearCachedSchema
    private volatile Schema schema;
    private Source schemaSource;
    // must be volatile because is accessed from different threads see ValidatorEndpoint.clearCachedSchema
    private volatile SchemaFactory schemaFactory;
    private URL schemaUrl;
    private File schemaFile;
    private byte[] schemaAsByteArray;
    private final String schemaResourceUri;
    private LSResourceResolver resourceResolver;
    
    private final CamelContext camelContext;
    
    
    public SchemaReader() {
        this.camelContext = null;
        this.schemaResourceUri = null;
    }
    
    /** Specify a camel context and a schema resource URI in order to read the schema via the class resolver specified in the Camel context. */
    public SchemaReader(CamelContext camelContext, String schemaResourceUri) {
        ObjectHelper.notNull(camelContext, "camelContext");
        ObjectHelper.notNull(schemaResourceUri, "schemaResourceUri");
        this.camelContext = camelContext;
        this.schemaResourceUri = schemaResourceUri;
    }

    public void loadSchema() throws Exception {
        // force loading of schema
        schema = createSchema();
    }

    // Properties
    // -----------------------------------------------------------------------

    public Schema getSchema() throws IOException, SAXException {
        if (schema == null) {
            synchronized (this) {
                if (schema == null) {
                    schema = createSchema();
                }
            }
        }
        return schema;
    }

    public void setSchema(Schema schema) {
        this.schema = schema;
    }

    public String getSchemaLanguage() {
        return schemaLanguage;
    }

    public void setSchemaLanguage(String schemaLanguage) {
        this.schemaLanguage = schemaLanguage;
    }

    public Source getSchemaSource() throws IOException {
        if (schemaSource == null) {
            schemaSource = createSchemaSource();
        }
        return schemaSource;
    }

    public void setSchemaSource(Source schemaSource) {
        this.schemaSource = schemaSource;
    }

    public URL getSchemaUrl() {
        return schemaUrl;
    }

    public void setSchemaUrl(URL schemaUrl) {
        this.schemaUrl = schemaUrl;
    }

    public File getSchemaFile() {
        return schemaFile;
    }

    public void setSchemaFile(File schemaFile) {
        this.schemaFile = schemaFile;
    }

    public byte[] getSchemaAsByteArray() {
        return schemaAsByteArray;
    }

    public void setSchemaAsByteArray(byte[] schemaAsByteArray) {
        this.schemaAsByteArray = schemaAsByteArray;
    }

    public SchemaFactory getSchemaFactory() {
        if (schemaFactory == null) {
            synchronized (this) {
                if (schemaFactory == null) {
                    schemaFactory = createSchemaFactory();
                }
            }
        }
        return schemaFactory;
    }

    public void setSchemaFactory(SchemaFactory schemaFactory) {
        this.schemaFactory = schemaFactory;
    }

    public LSResourceResolver getResourceResolver() {
        return resourceResolver;
    }

    public void setResourceResolver(LSResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    protected SchemaFactory createSchemaFactory() {
        SchemaFactory factory = SchemaFactory.newInstance(schemaLanguage);
        if (getResourceResolver() != null) {
            factory.setResourceResolver(getResourceResolver());
        }  
        if (camelContext == null || !Boolean.parseBoolean(camelContext.getGlobalOptions().get(ACCESS_EXTERNAL_DTD))) {
            try {
                LOG.debug("Configuring SchemaFactory to not allow access to external DTD/Schema");
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            } catch (SAXException e) {
                LOG.warn(e.getMessage(), e);
            } 
        }
        return factory;
    }

    protected Source createSchemaSource() throws IOException {
        throw new IllegalArgumentException("You must specify either a schema, schemaFile, schemaSource, schemaUrl, or schemaUri property");
    }

    protected Schema createSchema() throws SAXException, IOException {
        SchemaFactory factory = getSchemaFactory();

        URL url = getSchemaUrl();
        if (url != null) {
            synchronized (this) {
                return factory.newSchema(url);
            }
        }

        File file = getSchemaFile();
        if (file != null) {
            synchronized (this) {
                return factory.newSchema(file);
            }
        }

        byte[] bytes = getSchemaAsByteArray();
        if (bytes != null) {
            synchronized (this) {
                return factory.newSchema(new StreamSource(new ByteArrayInputStream(schemaAsByteArray)));
            }
        }
        
        if (schemaResourceUri != null) {
            synchronized (this) {
                bytes = readSchemaResource();
                return factory.newSchema(new StreamSource(new ByteArrayInputStream(bytes)));
            }          
        }
        
        Source source = getSchemaSource();
        synchronized (this) {
            return factory.newSchema(source);
        }

    }
    
    protected byte[] readSchemaResource() throws IOException {
        LOG.debug("reading schema resource: {}", schemaResourceUri);
        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, schemaResourceUri);
        byte[] bytes = null;
        try {
            bytes = getBytes(is);
        } finally {
            // and make sure to close the input stream after the schema has been
            // loaded
            IOHelper.close(is);
        }
        return bytes;
    }
    
    private static byte[] getBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOHelper.copy(IOHelper.buffered(stream), bos);
        return bos.toByteArray();
    }

}
