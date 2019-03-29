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
 * Camel specific {@link javax.xml.transform.URIResolver} which is capable of loading files
 * from classpath, file system and more.
 * <p/>
 * You can prefix with: classpath, file, http, ref, or bean.
 * classpath, file and http loads the resource using these protocols (classpath is default).
 * ref will lookup the resource in the registry.
 * bean will call a method on a bean to be used as the resource.
 * For bean you can specify the method name after dot, eg bean:myBean.myMethod
 */
public class XsltUriResolver implements URIResolver {

    private static final Logger LOG = LoggerFactory.getLogger(XsltUriResolver.class);

    private final CamelContext context;
    private final String location;
    private final String baseScheme;

    public XsltUriResolver(CamelContext context, String location) {
        this.context = context;
        this.location = location;
        if (ResourceHelper.hasScheme(location)) {
            baseScheme = ResourceHelper.getScheme(location);
        } else {
            // default to use classpath
            baseScheme = "classpath:";
        }
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
    
}
