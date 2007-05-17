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
package org.apache.camel.spring;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Producer;
import org.apache.camel.CamelTemplate;

/**
 * @version $Revision: $
 */
public class InjectedBean {

    // Endpoint
    //-----------------------------------------------------------------------
    @EndpointInject(uri = "direct:fieldInjectedEndpoint")
    private Endpoint fieldInjectedEndpoint;
    private Endpoint propertyInjectedEndpoint;

    public Endpoint getFieldInjectedEndpoint() {
        return fieldInjectedEndpoint;
    }

    public Endpoint getPropertyInjectedEndpoint() {
        return propertyInjectedEndpoint;
    }

    @EndpointInject(name = "namedEndpoint1")
    public void setPropertyInjectedEndpoint(Endpoint propertyInjectedEndpoint) {
        this.propertyInjectedEndpoint = propertyInjectedEndpoint;
    }


    // Producer
    //-----------------------------------------------------------------------
    @EndpointInject(uri = "direct:fieldInjectedProducer")
    private Producer fieldInjectedProducer;
    private Producer propertyInjectedProducer;


    public Producer getFieldInjectedProducer() {
        return fieldInjectedProducer;
    }

    public Producer getPropertyInjectedProducer() {
        return propertyInjectedProducer;
    }

    @EndpointInject(uri = "direct:propertyInjectedProducer")
    public void setPropertyInjectedProducer(Producer propertyInjectedProducer) {
        this.propertyInjectedProducer = propertyInjectedProducer;
    }


    // CamelTemplate
    //-----------------------------------------------------------------------
    @EndpointInject(uri = "direct:fieldInjectedCamelTemplate")
    private CamelTemplate fieldInjectedCamelTemplate;
    private CamelTemplate propertyInjectedCamelTemplate;

    public CamelTemplate getFieldInjectedCamelTemplate() {
        return fieldInjectedCamelTemplate;
    }

    public CamelTemplate getPropertyInjectedCamelTemplate() {
        return propertyInjectedCamelTemplate;
    }

    @EndpointInject(uri = "direct:propertyInjectedCamelTemplate")
    public void setPropertyInjectedCamelTemplate(CamelTemplate propertyInjectedCamelTemplate) {
        this.propertyInjectedCamelTemplate = propertyInjectedCamelTemplate;
    }
}
