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
package org.apache.camel.component.validator.msv;

import java.util.Map;

import javax.xml.validation.SchemaFactory;

import org.apache.camel.Endpoint;
import org.apache.camel.component.validator.ValidatorComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;

/**
 * The <a href="http://camel.apache.org/msv.html">MSV Component</a> uses the
 * <a href="https://msv.dev.java.net/">MSV library</a> to perform XML validation using pluggable XML schema languages.
 *
 */
@Component("msv")
public class MsvComponent extends ValidatorComponent {
    @Metadata(label = "advanced")
    private SchemaFactory schemaFactory;

    public MsvComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        MsvEndpoint endpoint = new MsvEndpoint(uri, this, remaining);
        endpoint.setSchemaFactory(getSchemaFactory());
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public SchemaFactory getSchemaFactory() {
        return schemaFactory;
    }

    /**
     * To use the {@link javax.xml.validation.SchemaFactory}.
     */
    public void setSchemaFactory(SchemaFactory schemaFactory) {
        this.schemaFactory = schemaFactory;
    }

}
