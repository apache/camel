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
package org.apache.camel.component.xslt;

import java.util.Map;

import javax.xml.transform.TransformerFactory;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.xml.XsltBuilder;
import org.apache.camel.component.ResourceBasedComponent;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.springframework.core.io.Resource;

/**
 * An <a href="http://activemq.apache.org/camel/xslt.html">XSLT Component</a>
 * for performing XSLT transforms of messages
 *
 * @version $Revision$
 */
public class XsltComponent extends ResourceBasedComponent {
    private XmlConverter xmlConverter;

    public XmlConverter getXmlConverter() {
        return xmlConverter;
    }

    public void setXmlConverter(XmlConverter xmlConverter) {
        this.xmlConverter = xmlConverter;
    }

    protected Endpoint<Exchange> createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        Resource resource = resolveMandatoryResource(remaining);
        if (log.isDebugEnabled()) {
            log.debug(this + " using schema resource: " + resource);
        }
        XsltBuilder xslt = newInstance(XsltBuilder.class);

        // lets allow the converter to be configured
        XmlConverter converter = null;
        String converterName = getAndRemoveParameter(parameters, "converter", String.class);        
        if (converterName != null) {
            converter = mandatoryLookup(converterName, XmlConverter.class);
        }
        if (converter == null) {
            converter = getXmlConverter();
        }
        if (converter != null) {
            xslt.setConverter(converter);
        }
        
        String transformerFactoryClassName = getAndRemoveParameter(parameters, "transformerFactoryClass", String.class);
        TransformerFactory factory = null;
        if (transformerFactoryClassName != null) {
            Class factoryClass = ObjectHelper.loadClass(transformerFactoryClassName);
            if (factoryClass != null) {
                factory = (TransformerFactory) newInstance(factoryClass);
            } else {
                log.warn("Can't find the TransformerFactoryClass with the class name " + transformerFactoryClassName);
            }
        }
        
        String transformerFactoryName = getAndRemoveParameter(parameters, "transformerFactory", String.class);        
        if (transformerFactoryName != null) {
            factory = mandatoryLookup(transformerFactoryName, TransformerFactory.class);
        }
        
        if (factory != null) {
            xslt.getConverter().setTransformerFactory(factory);
        }
        xslt.setTransformerInputStream(resource.getInputStream());
        configureXslt(xslt, uri, remaining, parameters);
        return new ProcessorEndpoint(uri, this, xslt);
    }

    protected void configureXslt(XsltBuilder xslt, String uri, String remaining, Map parameters) throws Exception {
        setProperties(xslt, parameters);
    }
}
