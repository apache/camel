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

package org.apache.camel.model.transformer;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * To load custom transformers from classpath scanning to be used for route level transformations.
 */
@Metadata(label = "transformation")
@XmlRootElement(name = "loadTransformer")
@XmlAccessorType(XmlAccessType.FIELD)
public class LoadTransformerDefinition extends TransformerDefinition {

    @XmlAttribute
    private String packageScan;

    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "false")
    private String defaults;

    public LoadTransformerDefinition() {}

    protected LoadTransformerDefinition(LoadTransformerDefinition source) {
        super(source);
        this.packageScan = source.packageScan;
        this.defaults = source.defaults;
    }

    @Override
    public LoadTransformerDefinition copyDefinition() {
        return new LoadTransformerDefinition(this);
    }

    public String getDefaults() {
        return defaults;
    }

    /**
     * Enable loading of default transformers.
     */
    public void setDefaults(String defaults) {
        this.defaults = defaults;
    }

    public String getPackageScan() {
        return packageScan;
    }

    /**
     * Set the classpath location to scan for annotated transformers.
     */
    public void setPackageScan(String packageScan) {
        this.packageScan = packageScan;
    }
}
