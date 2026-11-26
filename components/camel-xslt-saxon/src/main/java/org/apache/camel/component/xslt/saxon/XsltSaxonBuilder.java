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
package org.apache.camel.component.xslt.saxon;

import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stax.StAXSource;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.jaxp.TemplatesImpl;
import net.sf.saxon.jaxp.TransformerImpl;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.StandardMessageHandler;
import net.sf.saxon.lib.StandardUnparsedTextResolver;
import net.sf.saxon.lib.UnparsedTextURIResolver;
import net.sf.saxon.str.UnicodeWriter;
import net.sf.saxon.str.UnicodeWriterToWriter;
import net.sf.saxon.trans.XPathException;
import org.apache.camel.Exchange;
import org.apache.camel.component.xslt.XmlSourceHandlerFactoryImpl;
import org.apache.camel.component.xslt.XsltBuilder;
import org.apache.camel.component.xslt.XsltMessageLogger;
import org.apache.camel.component.xslt.XsltUriResolver;
import org.apache.camel.support.builder.xml.StAX2SAXSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XsltSaxonBuilder extends XsltBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(XsltSaxonBuilder.class);

    // the same protocols XsltUriResolver governs for document(); Camel's classpath:, ref: and bean: are internal
    // resource schemes outside the JAXP external-access model and are always resolved
    private static final Set<String> EXTERNAL_PROTOCOLS = Set.of("http", "https", "ftp", "file");

    private boolean allowStAX = true;
    private boolean useJsonBody = false;
    private boolean secureProcessing = true;
    // the external protocols unparsed-text() and json-doc() may read at transform time, resolved once from the Saxon
    // configuration as it is fixed once the builder is initialized
    private volatile ExternalAccess runtimeExternalAccess;

    @Override
    protected Source prepareSource(Source source) {
        if (!isAllowStAX() && source instanceof StAXSource) {
            // Always convert StAXSource to SAXSource.
            // * Xalan and Saxon-B don't support StAXSource.
            // * The JDK default implementation (XSLTC) doesn't handle CDATA events
            //   (see com.sun.org.apache.xalan.internal.xsltc.trax.StAXStream2SAX).
            // * Saxon-HE/PE/EE seem to support StAXSource, but don't advertise this
            //   officially (via TransformerFactory.getFeature(StAXSource.FEATURE))
            source = new StAX2SAXSource(((StAXSource) source).getXMLStreamReader());
        }
        return source;
    }

    // Properties
    // -------------------------------------------------------------------------

    public boolean isAllowStAX() {
        return allowStAX;
    }

    public void setAllowStAX(boolean allowStAX) {
        this.allowStAX = allowStAX;
    }

    public boolean isUseJsonBody() {
        return useJsonBody;
    }

    public void setUseJsonBody(boolean useJsonBody) {
        this.useJsonBody = useJsonBody;
    }

    public boolean isSecureProcessing() {
        return secureProcessing;
    }

    public void setSecureProcessing(boolean secureProcessing) {
        this.secureProcessing = secureProcessing;
    }

    @Override
    protected XmlSourceHandlerFactoryImpl createXmlSourceHandlerFactoryImpl() {
        SaxonXmlSourceHandlerFactoryImpl factory = new SaxonXmlSourceHandlerFactoryImpl();
        factory.setUseJsonBody(useJsonBody);
        factory.setSecureProcessing(secureProcessing);
        return factory;
    }

    @Override
    protected void configureTransformer(Transformer transformer, Exchange exchange) throws Exception {
        super.configureTransformer(transformer, exchange);
        if (transformer instanceof TransformerImpl saxonTransformer) {
            installRuntimeUnparsedTextResolver(saxonTransformer.getUnderlyingController());
        }
    }

    /**
     * Applies the runtime {@code document()} restriction to the XSLT 3.0 functions {@code unparsed-text()},
     * {@code unparsed-text-lines()}, {@code unparsed-text-available()} and {@code json-doc()}. Saxon does not read
     * their resources through the {@link javax.xml.transform.URIResolver} installed on the transformer, but through its
     * {@link UnparsedTextURIResolver}, which resolves against the unrestricted resolver used for stylesheet
     * compilation. So when Camel's own {@link XsltUriResolver} is in use and the transformer factory restricts external
     * stylesheet access, a restricting resolver is installed. It is installed on the Saxon configuration, as each
     * controller takes its resolver from there (also when the transformer is reset for reuse, and for a transformation
     * started at run time with {@code fn:transform()}), and on the controller of this transformer, which may have been
     * created before. This happens at the first transform, after the stylesheet was compiled; {@code xsl:include} /
     * {@code xsl:import} are resolved through the {@link javax.xml.transform.URIResolver} and are unaffected either
     * way. ({@code collection()} and {@code uri-collection()} are already restricted by Saxon itself.)
     */
    private void installRuntimeUnparsedTextResolver(Controller controller) {
        if (controller.getUnparsedTextURIResolver() instanceof RestrictedUnparsedTextResolver
                || !(getUriResolver() instanceof XsltUriResolver)) {
            return;
        }
        Configuration configuration = controller.getConfiguration();
        Set<String> allowedExternalProtocols = resolveRuntimeAllowedExternalProtocols(configuration);
        if (allowedExternalProtocols == null) {
            return;
        }
        UnparsedTextURIResolver resolver = configuration.getUnparsedTextURIResolver();
        if (!(resolver instanceof RestrictedUnparsedTextResolver)) {
            resolver = new RestrictedUnparsedTextResolver(
                    resolver != null ? resolver : new StandardUnparsedTextResolver(), allowedExternalProtocols);
            configuration.setUnparsedTextURIResolver(resolver);
        }
        controller.setUnparsedTextURIResolver(resolver);
    }

    /**
     * Reads the external protocols the runtime resolver may load. Saxon keeps the factory's
     * {@code ACCESS_EXTERNAL_STYLESHEET} attribute as its {@link Feature#ALLOWED_PROTOCOLS} configuration property,
     * which is what the factory's {@code getAttribute} returns. Returns {@code null} (unrestricted) when it is
     * {@code "all"}, which is also the Saxon default when the attribute is not set.
     */
    private Set<String> resolveRuntimeAllowedExternalProtocols(Configuration configuration) {
        ExternalAccess access = runtimeExternalAccess;
        if (access == null) {
            String value = configuration.getConfigurationProperty(Feature.ALLOWED_PROTOCOLS);
            access = new ExternalAccess(XsltUriResolver.parseAllowedProtocols(value));
            runtimeExternalAccess = access;
        }
        return access.allowedProtocols();
    }

    @Override
    protected Templates createTemplates(TransformerFactory factory, Source source) throws TransformerConfigurationException {
        final Templates templates = super.createTemplates(factory, source);
        if (templates instanceof TemplatesImpl && getXsltMessageLogger() != null) {
            return new MessageDelegatingTemplates((TemplatesImpl) templates, getXsltMessageLogger());
        }
        return templates;
    }

    /**
     * The resolved runtime external access; {@code allowedProtocols} is {@code null} when external access is
     * unrestricted.
     */
    private record ExternalAccess(Set<String> allowedProtocols) {
    }

    /**
     * Enforces the {@code ACCESS_EXTERNAL_STYLESHEET} restriction for the resources Saxon reads through its
     * {@link UnparsedTextURIResolver} ({@code unparsed-text()}, {@code unparsed-text-lines()},
     * {@code unparsed-text-available()} and {@code json-doc()}), the same way {@link XsltUriResolver} does for
     * {@code document()}: a forbidden external protocol throws an {@link XPathException}, so the function fails (or
     * {@code unparsed-text-available()} returns false) instead of reading the resource. Only the standard external
     * protocols are governed; Camel's {@code classpath:}, {@code ref:} and {@code bean:} schemes are passed to the
     * delegate resolver, as are the allowed protocols.
     */
    static final class RestrictedUnparsedTextResolver implements UnparsedTextURIResolver {

        private final UnparsedTextURIResolver delegate;
        private final Set<String> allowedExternalProtocols;

        RestrictedUnparsedTextResolver(UnparsedTextURIResolver delegate, Set<String> allowedExternalProtocols) {
            this.delegate = delegate;
            this.allowedExternalProtocols = allowedExternalProtocols;
        }

        @Override
        public Reader resolve(URI absoluteURI, String encoding, Configuration config, boolean fallback)
                throws XPathException {
            String scheme = absoluteURI.getScheme();
            // compare case-insensitively so an upper/mixed-case scheme cannot slip past the guard
            String protocol = scheme != null ? scheme.toLowerCase(Locale.ROOT) : null;
            if (protocol != null && EXTERNAL_PROTOCOLS.contains(protocol) && !allowedExternalProtocols.contains(protocol)) {
                // log the denial explicitly: unparsed-text-available() turns this exception into false, so this WARN
                // is what actually tells an operator why the function did not read the resource
                LOG.warn("Refusing to resolve external resource {} for the XSLT unparsed-text() or json-doc() function:"
                         + " protocol '{}' is not permitted by the transformer factory's ACCESS_EXTERNAL_STYLESHEET"
                         + " restriction",
                        absoluteURI, protocol);
                throw new XPathException(
                        "Refusing to resolve external resource " + absoluteURI
                                         + " for the XSLT unparsed-text() or json-doc() function: protocol '" + protocol
                                         + "' is not permitted by the transformer factory's ACCESS_EXTERNAL_STYLESHEET restriction",
                        "FOUT1170");
            }
            return delegate.resolve(absoluteURI, encoding, config, fallback);
        }
    }

    private static class MessageConsumerWriter extends Writer {

        private final XsltMessageLogger xsltMessageLogger;

        public MessageConsumerWriter(XsltMessageLogger xsltMessageLogger) {
            this.xsltMessageLogger = xsltMessageLogger;
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            if (len > 0) {
                xsltMessageLogger.accept(String.copyValueOf(cbuf, off, len));
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            flush();
        }
    }

    static class MessageDelegatingTemplates implements Templates {

        private final TemplatesImpl delegated;

        private final XsltMessageLogger xsltMessageLogger;

        MessageDelegatingTemplates(TemplatesImpl templates, XsltMessageLogger xsltMessageLogger) {
            this.delegated = templates;
            this.xsltMessageLogger = xsltMessageLogger;
        }

        @Override
        public Transformer newTransformer() throws TransformerConfigurationException {
            final TransformerImpl transformer = (TransformerImpl) delegated.newTransformer();
            final StandardMessageHandler standardMessageHandler = new StandardMessageHandler(transformer.getConfiguration());
            final UnicodeWriter writer = new UnicodeWriterToWriter(new MessageConsumerWriter(xsltMessageLogger));
            standardMessageHandler.setUnicodeWriter(writer);
            transformer.getUnderlyingXsltTransformer().setMessageHandler(standardMessageHandler);

            return transformer;
        }

        @Override
        public Properties getOutputProperties() {
            return delegated.getOutputProperties();
        }
    }
}
