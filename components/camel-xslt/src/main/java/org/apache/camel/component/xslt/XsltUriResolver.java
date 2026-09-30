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
package org.apache.camel.component.xslt;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Camel specific {@link javax.xml.transform.URIResolver} which is capable of loading files from classpath, file system
 * and more.
 * <p/>
 * You can prefix with: classpath, file, http, ref, or bean. classpath, file and http loads the resource using these
 * protocols (classpath is default). ref will lookup the resource in the registry. bean will call a method on a bean to
 * be used as the resource. For bean you can specify the method name after dot, eg bean:myBean.myMethod
 */
public class XsltUriResolver implements URIResolver {

    private static final Logger LOG = LoggerFactory.getLogger(XsltUriResolver.class);

    // protocols governed by the JAXP ACCESS_EXTERNAL_STYLESHEET attribute; Camel's classpath:, ref: and bean: are
    // internal resource schemes outside the JAXP external-access model and are always resolved
    private static final Set<String> EXTERNAL_PROTOCOLS = Set.of("http", "https", "ftp", "file");

    private final CamelContext context;
    private final String location;
    private final String baseScheme;
    private final Set<String> allowedExternalProtocols;

    public XsltUriResolver(CamelContext context, String location) {
        this(context, location, null);
    }

    /**
     * @param allowedExternalProtocols the external protocols this resolver may load, mirroring the factory's
     *                                 {@code ACCESS_EXTERNAL_STYLESHEET}; {@code null} leaves external access
     *                                 unrestricted (the default), an empty set forbids every external protocol
     */
    public XsltUriResolver(CamelContext context, String location, Set<String> allowedExternalProtocols) {
        this.context = context;
        this.location = location;
        this.allowedExternalProtocols = allowedExternalProtocols;
        if (ResourceHelper.hasScheme(location)) {
            baseScheme = ResourceHelper.getScheme(location);
        } else {
            // default to use classpath
            baseScheme = "classpath:";
        }
    }

    /**
     * Returns a copy of this resolver that additionally enforces the given external-protocol allow-list, preserving the
     * {@link CamelContext} and location so relative resolution is unchanged. Used to install a restricted resolver at
     * transform time (for {@code document()}) while leaving stylesheet compilation unrestricted.
     */
    public XsltUriResolver withAllowedExternalProtocols(Set<String> allowedExternalProtocols) {
        return new XsltUriResolver(context, location, allowedExternalProtocols);
    }

    /**
     * Parses a JAXP {@code ACCESS_EXTERNAL_*} attribute value into the set of allowed protocols, or {@code null} when
     * access is unrestricted. The value is a comma-separated protocol list; {@code "all"} means unrestricted and an
     * empty string forbids every external protocol.
     */
    public static Set<String> parseAllowedProtocols(String accessExternalValue) {
        if (accessExternalValue == null || "all".equals(accessExternalValue.trim())) {
            return null;
        }
        Set<String> protocols = new HashSet<>();
        for (String protocol : accessExternalValue.split(",")) {
            String trimmed = protocol.trim();
            if (!trimmed.isEmpty()) {
                protocols.add(trimmed);
            }
        }
        return protocols;
    }

    @Override
    public Source resolve(String href, String base) throws TransformerException {
        // supports the empty href
        if (ObjectHelper.isEmpty(href)) {
            href = location;
        }
        if (ObjectHelper.isEmpty(href)) {
            throw new TransformerException("include href is empty");
        }

        LOG.trace("Resolving URI with href: {} and base: {}", href, base);

        String scheme = ResourceHelper.getScheme(href);

        if (scheme != null) {
            checkExternalAccessAllowed(href, scheme);
            // need to compact paths for file/classpath as it can be relative paths using .. to go backwards
            String hrefPath = StringHelper.after(href, scheme);
            if ("file:".equals(scheme)) {
                // compact path use file OS separator
                href = scheme + FileUtil.compactPath(hrefPath);
            } else if ("classpath:".equals(scheme)) {
                // for classpath always use /
                href = scheme + FileUtil.compactPath(hrefPath, '/');
            }
            LOG.debug("Resolving URI from {}: {}", scheme, href);

            InputStream is;
            try {
                is = ResourceHelper.resolveMandatoryResourceAsInputStream(context, href);
            } catch (IOException e) {
                throw new TransformerException(e);
            }
            return new StreamSource(is, href);
        }

        // if href and location is the same, then its the initial resolve
        if (href.equals(location)) {
            String path = baseScheme + href;
            return resolve(path, base);
        }

        // okay then its relative to the starting location from the XSLT importing this one
        String path = FileUtil.onlyPath(base);
        if (ObjectHelper.isEmpty(path)) {
            path = baseScheme + href;
            return resolve(path, base);
        } else {
            if (ResourceHelper.hasScheme(path)) {
                path = path + "/" + href;
            } else {
                path = baseScheme + path + "/" + href;
            }
            return resolve(path, base);
        }
    }

    /**
     * Enforces the configured {@code ACCESS_EXTERNAL_STYLESHEET} restriction by throwing a {@link TransformerException}
     * for a forbidden external protocol. JAXP applies that attribute only when no custom {@link URIResolver} returns a
     * {@link Source}, and Camel always installs this resolver, so it must apply the same limit itself. Throwing matches
     * plain JAXP behaviour: the processor reports an access error rather than silently reading the resource (the JDK's
     * XSLTC turns a resolver exception into a document retrieval failure and does not fall back to reading it). Only
     * the standard external protocols are governed; Camel's {@code classpath:}, {@code ref:} and {@code bean:} schemes
     * are internal lookups outside the JAXP model and are always resolved. Does nothing when external access is
     * unrestricted ({@code allowedExternalProtocols == null}).
     */
    private void checkExternalAccessAllowed(String href, String scheme) throws TransformerException {
        if (allowedExternalProtocols == null) {
            return;
        }
        // scheme carries a trailing ':' (e.g. "http:"); compare case-insensitively so an upper/mixed-case scheme
        // cannot slip past the guard
        String protocol = scheme.endsWith(":") ? scheme.substring(0, scheme.length() - 1) : scheme;
        protocol = protocol.toLowerCase(Locale.ROOT);
        if (EXTERNAL_PROTOCOLS.contains(protocol) && !allowedExternalProtocols.contains(protocol)) {
            // log the denial explicitly: the XSLT processor turns this exception into a generic document retrieval
            // failure, so this WARN is what actually tells an operator why document() did not read the resource
            LOG.warn("Refusing to resolve external resource {} for the XSLT document() function: protocol '{}' is not"
                     + " permitted by the transformer factory's ACCESS_EXTERNAL_STYLESHEET restriction",
                    href, protocol);
            throw new TransformerException(
                    "Refusing to resolve external resource " + href + " for the XSLT document() function: protocol '"
                                           + protocol
                                           + "' is not permitted by the transformer factory's ACCESS_EXTERNAL_STYLESHEET restriction");
        }
    }

}
