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
package org.apache.camel.processor.validation;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

/**
 * Reads the schema used in the processor {@link ValidatingProcessor}. Contains
 * the method {@link clearCachedSchema()} to force re-reading the schema.
 */
public class SchemaReader {

    private String schemaLanguage = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    // must be volatile because is accessed from different threads see ValidatorEndpoint.clearCachedSchema
    private volatile Schema schema;
    private Source schemaSource;
    // must be volatile because is accessed from different threads see ValidatorEndpoint.clearCachedSchema
    private volatile SchemaFactory schemaFactory;
    private URL schemaUrl;
    private File schemaFile;
    private volatile byte[] schemaAsByteArray;
    private LSResourceResolver resourceResolver;

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
        return factory;
    }

    protected Source createSchemaSource() throws IOException {
        throw new IllegalArgumentException("You must specify either a schema, schemaFile, schemaSource or schemaUrl property");
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

        Source source = getSchemaSource();
        synchronized (this) {
            return factory.newSchema(source);
        }
    }

}
