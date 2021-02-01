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
package org.apache.camel.xml.jaxb;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.bind.Binder;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import org.apache.camel.CamelContext;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.XMLRoutesDefinitionLoader;
import org.apache.camel.spi.annotations.JdkService;

import static org.apache.camel.xml.jaxb.JaxbHelper.applyNamespaces;
import static org.apache.camel.xml.jaxb.JaxbHelper.extractNamespaces;
import static org.apache.camel.xml.jaxb.JaxbHelper.getJAXBContext;
import static org.apache.camel.xml.jaxb.JaxbHelper.newXmlConverter;

/**
 * JAXB based {@link XMLRoutesDefinitionLoader}. This is the default loader used historically by Camel. The camel-xml-io
 * parser is a light-weight alternative.
 */
@JdkService(XMLRoutesDefinitionLoader.FACTORY)
public class JaxbXMLRoutesDefinitionLoader implements XMLRoutesDefinitionLoader {

    @Override
    public Object loadRoutesDefinition(CamelContext context, InputStream inputStream) throws Exception {
        XmlConverter xmlConverter = newXmlConverter(context);
        Document dom = xmlConverter.toDOMDocument(inputStream, null);

        JAXBContext jaxbContext = getJAXBContext(context);

        Map<String, String> namespaces = new LinkedHashMap<>();
        extractNamespaces(dom, namespaces);

        Binder<Node> binder = jaxbContext.createBinder();
        Object result = binder.unmarshal(dom);

        if (result == null) {
            throw new JAXBException("Cannot unmarshal to RoutesDefinition using JAXB");
        }

        // can either be routes or a single route
        RoutesDefinition answer;
        if (result instanceof RouteDefinition) {
            RouteDefinition route = (RouteDefinition) result;
            answer = new RoutesDefinition();
            applyNamespaces(route, namespaces);
            answer.getRoutes().add(route);
        } else if (result instanceof RoutesDefinition) {
            answer = (RoutesDefinition) result;
            for (RouteDefinition route : answer.getRoutes()) {
                applyNamespaces(route, namespaces);
            }
        } else {
            // ignore not supported type
            return null;
        }

        return answer;
    }

    @Override
    public Object loadRouteTemplatesDefinition(CamelContext context, InputStream inputStream) throws Exception {
        XmlConverter xmlConverter = newXmlConverter(context);
        Document dom = xmlConverter.toDOMDocument(inputStream, null);

        JAXBContext jaxbContext = getJAXBContext(context);

        Map<String, String> namespaces = new LinkedHashMap<>();
        extractNamespaces(dom, namespaces);

        Binder<Node> binder = jaxbContext.createBinder();
        Object result = binder.unmarshal(dom);

        if (result == null) {
            throw new JAXBException("Cannot unmarshal to RouteTemplatesDefinition using JAXB");
        }

        // can either be routes or a single route
        RouteTemplatesDefinition answer;
        if (result instanceof RouteTemplateDefinition) {
            RouteTemplateDefinition route = (RouteTemplateDefinition) result;
            answer = new RouteTemplatesDefinition();
            applyNamespaces(route.getRoute(), namespaces);
            answer.getRouteTemplates().add(route);
        } else if (result instanceof RouteTemplatesDefinition) {
            answer = (RouteTemplatesDefinition) result;
            for (RouteTemplateDefinition route : answer.getRouteTemplates()) {
                applyNamespaces(route.getRoute(), namespaces);
            }
        } else {
            // ignore not supported type
            return null;
        }

        return answer;
    }

    @Override
    public Object loadRestsDefinition(CamelContext context, InputStream inputStream) throws Exception {
        // load routes using JAXB
        Unmarshaller unmarshaller = getJAXBContext(context).createUnmarshaller();
        Object result = unmarshaller.unmarshal(inputStream);

        if (result == null) {
            throw new IOException("Cannot unmarshal to rests using JAXB from input stream: " + inputStream);
        }

        // can either be routes or a single route
        RestsDefinition answer;
        if (result instanceof RestDefinition) {
            RestDefinition rest = (RestDefinition) result;
            answer = new RestsDefinition();
            answer.getRests().add(rest);
        } else if (result instanceof RestsDefinition) {
            answer = (RestsDefinition) result;
        } else {
            // ignore not supported type
            return null;
        }

        return answer;
    }

    @Override
    public String toString() {
        return "camel-xml-jaxb";
    }

}
