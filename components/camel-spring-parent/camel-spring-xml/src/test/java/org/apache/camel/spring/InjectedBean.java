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
import org.apache.camel.PollingConsumer;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;

public class InjectedBean {
    @EndpointInject("direct:fieldInjectedEndpoint")
    private Endpoint fieldInjectedEndpoint;
    private Endpoint propertyInjectedEndpoint;
    @EndpointInject("direct:fieldInjectedProducer")
    private Producer fieldInjectedProducer;
    private Producer propertyInjectedProducer;
    @EndpointInject("direct:fieldInjectedCamelTemplate")
    private ProducerTemplate fieldInjectedCamelTemplate;
    private ProducerTemplate propertyInjectedCamelTemplate;
    @EndpointInject
    private ProducerTemplate injectByFieldName;
    private ProducerTemplate injectByPropertyName;
    @EndpointInject("direct:fieldInjectedEndpoint")
    private PollingConsumer fieldInjectedPollingConsumer;
    private PollingConsumer propertyInjectedPollingConsumer;

    // Endpoint
    //-----------------------------------------------------------------------
    public Endpoint getFieldInjectedEndpoint() {
        return fieldInjectedEndpoint;
    }

    public Endpoint getPropertyInjectedEndpoint() {
        return propertyInjectedEndpoint;
    }

    @EndpointInject("ref:namedEndpoint1")
    public void setPropertyInjectedEndpoint(Endpoint propertyInjectedEndpoint) {
        this.propertyInjectedEndpoint = propertyInjectedEndpoint;
    }

    // Producer
    //-----------------------------------------------------------------------

    public Producer getFieldInjectedProducer() {
        return fieldInjectedProducer;
    }

    public Producer getPropertyInjectedProducer() {
        return propertyInjectedProducer;
    }

    @EndpointInject("direct:propertyInjectedProducer")
    public void setPropertyInjectedProducer(Producer propertyInjectedProducer) {
        this.propertyInjectedProducer = propertyInjectedProducer;
    }

    // CamelTemplate
    //-----------------------------------------------------------------------
    public ProducerTemplate getFieldInjectedCamelTemplate() {
        return fieldInjectedCamelTemplate;
    }

    public ProducerTemplate getPropertyInjectedCamelTemplate() {
        return propertyInjectedCamelTemplate;
    }

    @EndpointInject("direct:propertyInjectedCamelTemplate")
    public void setPropertyInjectedCamelTemplate(ProducerTemplate propertyInjectedCamelTemplate) {
        this.propertyInjectedCamelTemplate = propertyInjectedCamelTemplate;
    }

    // ProducerTemplate
    //-------------------------------------------------------------------------

    public ProducerTemplate getInjectByFieldName() {
        return injectByFieldName;
    }

    public void setInjectByFieldName(ProducerTemplate injectByFieldName) {
        this.injectByFieldName = injectByFieldName;
    }

    public ProducerTemplate getInjectByPropertyName() {
        return injectByPropertyName;
    }

    @EndpointInject
    public void setInjectByPropertyName(ProducerTemplate injectByPropertyName) {
        this.injectByPropertyName = injectByPropertyName;
    }

    // PollingConsumer
    //-------------------------------------------------------------------------

    public PollingConsumer getFieldInjectedPollingConsumer() {
        return fieldInjectedPollingConsumer;
    }

    public void setFieldInjectedPollingConsumer(PollingConsumer fieldInjectedPollingConsumer) {
        this.fieldInjectedPollingConsumer = fieldInjectedPollingConsumer;
    }

    public PollingConsumer getPropertyInjectedPollingConsumer() {
        return propertyInjectedPollingConsumer;
    }

    @EndpointInject("direct:propertyInjectedPollingConsumer")
    public void setPropertyInjectedPollingConsumer(PollingConsumer propertyInjectedPollingConsumer) {
        this.propertyInjectedPollingConsumer = propertyInjectedPollingConsumer;
    }
}
