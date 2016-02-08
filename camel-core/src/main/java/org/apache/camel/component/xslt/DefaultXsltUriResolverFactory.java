package org.apache.camel.component.xslt;

import javax.xml.transform.URIResolver;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.xml.XsltUriResolver;

/**
 * Default URI resolver factory which instantiates the camel default XSLT URI
 * resolver which can resolves absolute and relative URIs in the classpath and
 * file system.
 */
public class DefaultXsltUriResolverFactory implements XsltUriResolverFactory {

    @Override
    public URIResolver createUriResolver(CamelContext camelContext, String resourceUri) {
        return new XsltUriResolver(camelContext.getClassResolver(), resourceUri);
    }

}
