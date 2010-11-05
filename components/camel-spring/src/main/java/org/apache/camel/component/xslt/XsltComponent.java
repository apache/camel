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
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;

import org.apache.camel.Endpoint;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.xml.ResultHandlerFactory;
import org.apache.camel.builder.xml.XsltBuilder;
import org.apache.camel.builder.xml.XsltUriResolver;
import org.apache.camel.component.ResourceBasedComponent;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.springframework.core.io.Resource;

/**
 * An <a href="http://camel.apache.org/xslt.html">XSLT Component</a>
 * for performing XSLT transforms of messages
 *
 * @version $Revision$
 */
public class XsltComponent extends ResourceBasedComponent {
    private XmlConverter xmlConverter;
    private URIResolver uriResolver;

    public XmlConverter getXmlConverter() {
        return xmlConverter;
    }

    public void setXmlConverter(XmlConverter xmlConverter) {
        this.xmlConverter = xmlConverter;
    }

    public URIResolver getUriResolver() {
        return uriResolver;
    }

    public void setUriResolver(URIResolver uriResolver) {
        this.uriResolver = uriResolver;
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Resource resource = resolveMandatoryResource(remaining);
        if (log.isDebugEnabled()) {
            log.debug(this + " using schema resource: " + resource);
        }
        XsltBuilder xslt = getCamelContext().getInjector().newInstance(XsltBuilder.class);

        // lets allow the converter to be configured
        XmlConverter converter = resolveAndRemoveReferenceParameter(parameters, "converter", XmlConverter.class);
        if (converter == null) {
            converter = getXmlConverter();
        }
        if (converter != null) {
            xslt.setConverter(converter);
        }
        
        String transformerFactoryClassName = getAndRemoveParameter(parameters, "transformerFactoryClass", String.class);
        TransformerFactory factory = null;
        if (transformerFactoryClassName != null) {
            // provide the class loader of this component to work in OSGi environments
            Class<?> factoryClass = getCamelContext().getClassResolver().resolveClass(transformerFactoryClassName, XsltComponent.class.getClassLoader());
            if (factoryClass != null) {
                factory = (TransformerFactory) getCamelContext().getInjector().newInstance(factoryClass);
            } else {
                log.warn("Cannot find the TransformerFactoryClass with the class name: " + transformerFactoryClassName);
            }
        }
        
        if (parameters.get("transformerFactory") != null) {
            factory = resolveAndRemoveReferenceParameter(parameters, "transformerFactory", TransformerFactory.class);
        }
        
        if (factory != null) {
            xslt.getConverter().setTransformerFactory(factory);
        }

        // lookup custom resolver to use
        URIResolver resolver = resolveAndRemoveReferenceParameter(parameters, "uriResolver", URIResolver.class);
        if (resolver == null) {
            // not in endpoint then use component specific resolver
            resolver = getUriResolver();
        }
        if (resolver == null) {
            // fallback to use a Camel specific resolver
            resolver = new XsltUriResolver(getCamelContext().getClassResolver(), remaining);
        }
        // set resolver before input stream as resolver is used when loading the input stream
        xslt.setUriResolver(resolver);

        ResultHandlerFactory resultHandlerFactory = resolveAndRemoveReferenceParameter(parameters, "resultHandlerFactory", ResultHandlerFactory.class);
        if (resultHandlerFactory != null) {
            xslt.setResultHandlerFactory(resultHandlerFactory);
        }

        Boolean failOnNullBody = getAndRemoveParameter(parameters, "failOnNullBody", Boolean.class);
        if (failOnNullBody != null) {
            xslt.setFailOnNullBody(failOnNullBody);
        }
        String output = getAndRemoveParameter(parameters, "output", String.class);
        configureOutput(xslt, output);

        try {
            xslt.setTransformerInputStream(resource.getInputStream());
        } catch (Exception e) {
            // include information about the resource in the caused exception, so its easier for
            // end users to know which resource failed
            throw new TransformerConfigurationException(e.getMessage() + " " + resource.toString(), e);
        }
        configureXslt(xslt, uri, remaining, parameters);
        return new ProcessorEndpoint(uri, this, xslt);
    }

    protected void configureXslt(XsltBuilder xslt, String uri, String remaining, Map<String, Object> parameters) throws Exception {
        setProperties(xslt, parameters);
    }

    protected void configureOutput(XsltBuilder xslt, String output) throws Exception {
        if (ObjectHelper.isEmpty(output)) {
            return;
        }

        if ("string".equalsIgnoreCase(output)) {
            xslt.outputString();
        } else if ("bytes".equalsIgnoreCase(output)) {
            xslt.outputBytes();
        } else if ("DOM".equalsIgnoreCase(output)) {
            xslt.outputDOM();
        } else if ("file".equalsIgnoreCase(output)) {
            xslt.outputFile();
        } else {
            throw new IllegalArgumentException("Unknown output type: " + output);
        }
    }

}
