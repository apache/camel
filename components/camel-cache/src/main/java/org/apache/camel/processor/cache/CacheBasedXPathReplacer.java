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
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.component.cache.CacheConstants;
import org.apache.camel.component.cache.DefaultCacheManagerFactory;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheBasedXPathReplacer extends CacheValidate implements Processor, Service {
    private static final Logger LOG = LoggerFactory.getLogger(CacheBasedXPathReplacer.class);

    private CacheManager cacheManager;

    private String cacheName;
    private Expression key;
    private String xpath;

    public CacheBasedXPathReplacer(String cacheName, String key, String xpath) {
        this(cacheName, ExpressionBuilder.constantExpression(key), xpath);
    }

    public CacheBasedXPathReplacer(String cacheName, Expression key, String xpath) {
        if (cacheName.contains("cache://")) {
            this.setCacheName(cacheName.replace("cache://", ""));
        } else {
            this.setCacheName(cacheName);
        }
        this.key = key;
        this.xpath = xpath;
    }

    public void process(Exchange exchange) throws Exception {
        String cacheKey = key.evaluate(exchange, String.class);

        if (isValid(cacheManager, cacheName, cacheKey)) {
            Ehcache cache = cacheManager.getCache(cacheName);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Replacing XPath value {} in Message with value stored against key {} in CacheName {}",
                        new Object[]{xpath, cacheKey, cacheName});
            }
            exchange.getIn().setHeader(CacheConstants.CACHE_KEY, cacheKey);
            Object body = exchange.getIn().getBody();
            InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, body);
            Document document;
            try {
                document = exchange.getContext().getTypeConverter().convertTo(Document.class, exchange, is);
            } finally {
                IOHelper.close(is, "is", LOG);
            }

            InputStream cis = exchange.getContext().getTypeConverter()
                .convertTo(InputStream.class, cache.get(cacheKey).getObjectValue());

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
                DOMSource source = xmlConverter.toDOMSource(document);
                DOMResult result = new DOMResult();

                transformer.setParameter("cacheValue", cacheValueDocument);
                transformer.transform(source, result);

                // DOMSource can be converted to byte[] by camel type converter mechanism
                DOMSource dom = new DOMSource(result.getNode());
                exchange.getIn().setBody(dom, byte[].class);
            } finally {
                IOHelper.close(cis, "cis", LOG);
            }
        }
    }

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public Expression getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = ExpressionBuilder.constantExpression(key);
    }

    public void setKey(Expression key) {
        this.key = key;
    }

    public String getXpath() {
        return xpath;
    }

    public void setXpath(String xpath) {
        this.xpath = xpath;
    }

    @Override
    public void start() throws Exception {
        // Cache the buffer to the specified Cache against the specified key
        if (cacheManager == null) {
            cacheManager = new DefaultCacheManagerFactory().getInstance();
        }
    }

    @Override
    public void stop() throws Exception {
        // noop
    }
}
