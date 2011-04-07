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
package org.apache.camel.builder.xml;

import java.io.File;
import java.io.InputStream;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Camel specific {@link javax.xml.transform.URIResolver} which is capable of loading files
 * from the classpath and file system.
 * <p/>
 * Use prefix <tt>classpath:</tt> or <tt>file:</tt> to denote either classpath or file system.
 * If no prefix is provided then the prefix from the <tt>location</tt> parameter is used.
 * If it neither has a prefix then <tt>classpath:</tt> is used.
 * <p/>
 * This implementation <b>cannot</b> load files over http.
 *
 * @version 
 */
public class XsltUriResolver implements URIResolver {

    private static final transient Logger LOG = LoggerFactory.getLogger(XsltUriResolver.class); 

    private final ClassResolver resolver;
    private final String location;

    public XsltUriResolver(ClassResolver resolver, String location) {
        this.resolver = resolver;
        this.location = location;
    }

    public Source resolve(String href, String base) throws TransformerException {
        if (ObjectHelper.isEmpty(href)) {
            throw new TransformerException("include href is empty");
        }

        LOG.trace("Resolving URI with href: {} and base: {}", href, base);

        if (href.startsWith("classpath:")) {
            LOG.debug("Resolving URI from classpath: {}", href);

            String name = ObjectHelper.after(href, ":");
            InputStream is = resolver.loadResourceAsStream(name);
            if (is == null) {
                throw new TransformerException("Cannot find " + name + " in classpath");
            }
            return new StreamSource(is);
        }

        if (href.startsWith("file:")) {
            LOG.debug("Resolving URI from file: {}", href);

            String name = ObjectHelper.after(href, ":");
            File file = new File(name);
            return new StreamSource(file);
        }

        // okay then its relative to the starting location from the XSLT component
        String path = FileUtil.onlyPath(location);
        if (ObjectHelper.isEmpty(path)) {
            // default to use classpath: location
            path = "classpath:" + href;
            return resolve(path, base);
        } else {
            // default to use classpath: location
            path = "classpath:" + path + File.separator + href;
            return resolve(path, base);
        }
    }
    
}
