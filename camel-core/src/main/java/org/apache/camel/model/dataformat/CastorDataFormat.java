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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;

/**
 * Castor data format is used for unmarshal a XML payload to POJO or to marshal POJO back to XML payload.
 *
 * @version 
 */
@Metadata(firstVersion = "2.1.0", label = "dataformat,transformation,xml", title = "Castor")
@XmlRootElement(name = "castor")
@XmlAccessorType(XmlAccessType.FIELD)
@Deprecated
public class CastorDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private String mappingFile;
    @XmlAttribute
    @Metadata(defaultValue = "true")
    private Boolean whitelistEnabled = true;
    @XmlAttribute
    private String allowedUnmarshallObjects;
    @XmlAttribute
    private String deniedUnmarshallObjects;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean validation;
    @XmlAttribute @Metadata(defaultValue = "UTF-8")
    private String encoding;
    @XmlAttribute
    private String[] packages;
    @XmlAttribute
    private String[] classes;

    public CastorDataFormat() {
        super("castor");
    }

    public Boolean getValidation() {
        return validation;
    }

    /**
     * Whether validation is turned on or off.
     * <p/>
     * Is by default true.
     */
    public void setValidation(Boolean validation) {
        this.validation = validation;
    }

    public String getMappingFile() {
        return mappingFile;
    }

    /**
     * Path to a Castor mapping file to load from the classpath.
     */
    public void setMappingFile(String mappingFile) {
        this.mappingFile = mappingFile;
    }

    public String[] getPackages() {
        return packages;
    }

    /**
     * Add additional packages to Castor XmlContext
     */
    public void setPackages(String[] packages) {
        this.packages = packages;
    }

    public String[] getClasses() {
        return classes;
    }

    /**
     * Add additional class names to Castor XmlContext
     */
    public void setClasses(String[] classes) {
        this.classes = classes;
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * Encoding to use when marshalling an Object to XML.
     * <p/>
     * Is by default UTF-8
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public Boolean getWhitelistEnabled() {
        return whitelistEnabled;
    }

    /**
     * Define if Whitelist feature is enabled or not
     */
    public void setWhitelistEnabled(Boolean whitelistEnabled) {
        this.whitelistEnabled = whitelistEnabled;
    }

    public String getAllowedUnmarshallObjects() {
        return allowedUnmarshallObjects;
    }

    /**
     * Define the allowed objects to be unmarshalled.
     *
     * You can specify the FQN class name of allowed objects, and you can use comma to separate multiple entries.
     * It is also possible to use wildcards and regular expression which is based on the pattern
     * defined by {@link org.apache.camel.util.EndpointHelper#matchPattern(String, String)}.
     * Denied objects takes precedence over allowed objects.
     */
    public void setAllowedUnmarshallObjects(String allowedUnmarshallObjects) {
        this.allowedUnmarshallObjects = allowedUnmarshallObjects;
    }

    public String getDeniedUnmarshallObjects() {
        return deniedUnmarshallObjects;
    }

    /**
     * Define the denied objects to be unmarshalled.
     *
     * You can specify the FQN class name of deined objects, and you can use comma to separate multiple entries.
     * It is also possible to use wildcards and regular expression which is based on the pattern
     * defined by {@link org.apache.camel.util.EndpointHelper#matchPattern(String, String)}.
     * Denied objects takes precedence over allowed objects.
     */
    public void setDeniedUnmarshallObjects(String deniedUnmarshallObjects) {
        this.deniedUnmarshallObjects = deniedUnmarshallObjects;
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (mappingFile != null) {
            setProperty(camelContext, dataFormat, "mappingFile", mappingFile);
        }
        // should be true by default
        boolean isValidation = getValidation() == null || getValidation();
        setProperty(camelContext, dataFormat, "validation", isValidation);

        if (encoding != null) {
            setProperty(camelContext, dataFormat, "encoding", encoding);
        }
        if (packages != null) {
            setProperty(camelContext, dataFormat, "packages", packages);
        }
        if (classes != null) {
            setProperty(camelContext, dataFormat, "classes", classes);
        }
        if (whitelistEnabled != null) {
            setProperty(camelContext, dataFormat, "whitelistEnabled", whitelistEnabled);
        }
        if (allowedUnmarshallObjects != null) {
            setProperty(camelContext, dataFormat, "allowedUnmarshallObjects", allowedUnmarshallObjects);
        }
        if (deniedUnmarshallObjects != null) {
            setProperty(camelContext, dataFormat, "deniedUnmarshallObjects", deniedUnmarshallObjects);
        }
    }

}