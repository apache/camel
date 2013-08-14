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
package org.apache.camel.processor.cache;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cache.CacheConstants;
import org.apache.camel.component.cache.DefaultCacheManagerFactory;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheBasedXPathReplacer extends CacheValidate implements Processor {
    private static final Logger LOG = LoggerFactory.getLogger(CacheBasedXPathReplacer.class);
    private String cacheName;
    private String key;
    private String xpath;
    private CacheManager cacheManager;
    private Ehcache cache;
    private Document document;
    private DOMSource source;
    private DOMResult result;

    public CacheBasedXPathReplacer(String cacheName, String key, String xpath) {
        if (cacheName.contains("cache://")) {
            this.setCacheName(cacheName.replace("cache://", ""));
        } else {
            this.setCacheName(cacheName);
        }
        this.key = key;
        this.xpath = xpath;
    }

    public void process(Exchange exchange) throws Exception {
        // Cache the buffer to the specified Cache against the specified key
        cacheManager = new DefaultCacheManagerFactory().getInstance();

        if (isValid(cacheManager, cacheName, key)) {
            cache = cacheManager.getCache(cacheName);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Replacing XPath value {} in Message with value stored against key {} in CacheName {}",
                        new Object[]{xpath, key, cacheName});
            }
            exchange.getIn().setHeader(CacheConstants.CACHE_KEY, key);
            Object body = exchange.getIn().getBody();
            InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, body);
            try {
                document = exchange.getContext().getTypeConverter().convertTo(Document.class, exchange, is);
            } finally {
                IOHelper.close(is, "is", LOG);
            }

            InputStream cis = exchange.getContext().getTypeConverter()
                .convertTo(InputStream.class, cache.get(key).getObjectValue());

            try {
                Document cacheValueDocument = exchange.getContext().getTypeConverter()
                    .convertTo(Document.class, exchange, cis);

                // Create/setup the Transformer
                XmlConverter xmlConverter = new XmlConverter();
                String xslString = IOConverter.toString(new File("./src/main/resources/xpathreplacer.xsl"), exchange);
                xslString = xslString.replace("##match_token##", xpath);
                Source xslSource = xmlConverter.toStreamSource(new StringReader(xslString));
                TransformerFactory transformerFactory = xmlConverter.createTransformerFactory();
                Transformer transformer = transformerFactory.newTransformer(xslSource);
                source = xmlConverter.toDOMSource(document);
                result = new DOMResult();

                transformer.setParameter("cacheValue", cacheValueDocument);
                transformer.transform(source, result);
            } finally {
                IOHelper.close(cis, "cis", LOG);
            }
        }

        // DOMSource can be converted to byte[] by camel type converter mechanism
        DOMSource dom = new DOMSource(result.getNode());
        exchange.getIn().setBody(dom, byte[].class);
    }

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getXpath() {
        return xpath;
    }

    public void setXpath(String xpath) {
        this.xpath = xpath;
    }

}
