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
package org.apache.camel.language.xpath;

import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathFactory;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.LanguageSupport;

/**
 * XPath language.
 */
@Language("xpath")
public class XPathLanguage extends LanguageSupport {
    private Class<?> resultType;
    private QName resultQName;
    private Class<?> documentType;
    private XPathFactory xpathFactory;
    private Boolean useSaxon;
    private String objectModelUri;
    private Boolean threadSafety;
    private Boolean logNamespaces;
    private String headerName;

    @Override
    public Predicate createPredicate(String expression) {
        expression = loadResource(expression);

        XPathBuilder builder = XPathBuilder.xpath(expression);
        configureBuilder(builder);
        return builder;
    }

    @Override
    public Expression createExpression(String expression) {
        expression = loadResource(expression);

        XPathBuilder builder = XPathBuilder.xpath(expression);
        configureBuilder(builder);
        return builder;
    }

    @Override
    public Predicate createPredicate(Map<String, Object> properties) {
        return (Predicate) createExpression(properties);
    }

    @Override
    public Expression createExpression(Map<String, Object> properties) {
        String expression = (String) properties.get("expression");
        expression = loadResource(expression);

        Class<?> clazz = property(Class.class, properties, "documentType", null);
        if (clazz != null) {
            setDocumentType(clazz);
        }
        clazz = property(Class.class, properties, "resultType", null);
        if (clazz != null) {
            setResultType(clazz);
        }
        QName qname = property(QName.class, properties, "resultQName", null);
        if (qname != null) {
            setResultQName(qname);
        }
        setUseSaxon(property(Boolean.class, properties, "useSaxon", useSaxon));
        setObjectModelUri(property(String.class, properties, "objectModelUri", objectModelUri));
        setThreadSafety(property(Boolean.class, properties, "threadSafety", threadSafety));
        setLogNamespaces(property(Boolean.class, properties, "logNamespaces", logNamespaces));
        setHeaderName(property(String.class, properties, "headerName", headerName));
        setXpathFactory(property(XPathFactory.class, properties, "xpathFactory", xpathFactory));

        XPathBuilder builder = XPathBuilder.xpath(expression);
        configureBuilder(builder);
        return builder;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    public void setResultQName(QName qName) {
        this.resultQName = qName;
    }

    public QName getResultQName() {
        return resultQName;
    }

    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public Class<?> getDocumentType() {
        return documentType;
    }

    public void setDocumentType(Class<?> documentType) {
        this.documentType = documentType;
    }

    public XPathFactory getXpathFactory() {
        return xpathFactory;
    }

    public void setXpathFactory(XPathFactory xpathFactory) {
        this.xpathFactory = xpathFactory;
    }

    public void setUseSaxon(Boolean useSaxon) {
        this.useSaxon = useSaxon;
    }

    public Boolean getUseSaxon() {
        return useSaxon;
    }

    public String getObjectModelUri() {
        return objectModelUri;
    }

    public void setObjectModelUri(String objectModelUri) {
        this.objectModelUri = objectModelUri;
    }

    public Boolean getThreadSafety() {
        return threadSafety;
    }

    public void setThreadSafety(Boolean threadSafety) {
        this.threadSafety = threadSafety;
    }

    public Boolean getLogNamespaces() {
        return logNamespaces;
    }

    public void setLogNamespaces(Boolean logNamespaces) {
        this.logNamespaces = logNamespaces;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    private boolean isUseSaxon() {
        return useSaxon != null && useSaxon;
    }

    protected void configureBuilder(XPathBuilder builder) {
        if (threadSafety != null) {
            builder.setThreadSafety(threadSafety);
        }
        if (resultQName != null) {
            builder.setResultQName(resultQName);
        }
        if (resultType != null) {
            builder.setResultType(resultType);
        }
        if (logNamespaces != null) {
            builder.setLogNamespaces(logNamespaces);
        }
        if (headerName != null) {
            builder.setHeaderName(headerName);
        }
        if (documentType != null) {
            builder.setDocumentType(documentType);
        }

        if (isUseSaxon()) {
            builder.enableSaxon();
        } else {
            if (xpathFactory != null) {
                builder.setXPathFactory(xpathFactory);
            }
            if (objectModelUri != null) {
                builder.setObjectModelUri(objectModelUri);
            }
        }
    }

}
