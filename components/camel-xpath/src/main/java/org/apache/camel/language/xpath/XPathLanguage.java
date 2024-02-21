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
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.SingleInputTypedLanguageSupport;
import org.apache.camel.support.component.PropertyConfigurerSupport;

/**
 * XPath language.
 */
@Language("xpath")
public class XPathLanguage extends SingleInputTypedLanguageSupport implements PropertyConfigurer {
    private QName resultQName;
    private Class<?> documentType;
    private XPathFactory xpathFactory;
    private Boolean saxon;
    private String objectModelUri;
    private Boolean threadSafety;
    private Boolean logNamespaces;
    private Boolean preCompile;

    @Override
    public Predicate createPredicate(Expression source, String expression, Object[] properties) {
        expression = loadResource(expression);

        XPathBuilder builder = XPathBuilder.xpath(expression);
        configureBuilder(builder, properties, source);
        builder.setResultQName(XPathConstants.BOOLEAN); // use boolean for predicate mode
        return builder;
    }

    @Override
    public Expression createExpression(Expression source, String expression, Object[] properties) {
        expression = loadResource(expression);

        XPathBuilder builder = XPathBuilder.xpath(expression);
        configureBuilder(builder, properties, source);
        return builder;
    }

    public void setResultQName(QName qName) {
        this.resultQName = qName;
    }

    public QName getResultQName() {
        return resultQName;
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

    public Boolean getSaxon() {
        return saxon;
    }

    public void setSaxon(Boolean saxon) {
        this.saxon = saxon;
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

    public Boolean getPreCompile() {
        return preCompile;
    }

    public void setPreCompile(Boolean preCompile) {
        this.preCompile = preCompile;
    }

    protected void configureBuilder(XPathBuilder builder, Object[] properties, Expression source) {
        builder.setSource(source);

        Class<?> clazz = property(Class.class, properties, 2, documentType);
        if (clazz != null) {
            builder.setDocumentType(clazz);
        }
        QName qname = property(QName.class, properties, 3, resultQName);
        if (qname != null) {
            builder.setResultQName(qname);
        }
        Boolean bool = property(Boolean.class, properties, 4, saxon);
        if (bool != null) {
            builder.setUseSaxon(bool);
            if (bool) {
                builder.enableSaxon();
            }
        }
        if (!builder.isUseSaxon()) {
            // xpath factory can only be set if not saxon is enabled as saxon has its own factory and object model
            XPathFactory fac = property(XPathFactory.class, properties, 5, xpathFactory);
            if (fac != null) {
                builder.setXPathFactory(fac);
            }
            String str = property(String.class, properties, 6, objectModelUri);
            if (str != null) {
                builder.setObjectModelUri(str);
            }
        }
        bool = property(Boolean.class, properties, 7, threadSafety);
        if (bool != null) {
            builder.setThreadSafety(bool);
        }
        bool = property(Boolean.class, properties, 8, preCompile);
        if (bool != null) {
            builder.setPreCompile(bool);
        }
        bool = property(Boolean.class, properties, 9, logNamespaces);
        if (bool != null) {
            builder.setLogNamespaces(bool);
        }
        Map<String, String> ns = property(Map.class, properties, 10, null);
        if (ns != null && !ns.isEmpty()) {
            builder.setNamespaces(ns);
        }
        // must set result type last as it influence the QName in use
        clazz = property(Class.class, properties, 0, null);
        if (clazz != null) {
            builder.setResultType(clazz);
        }
    }

    @Override
    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
        if (target != this) {
            throw new IllegalStateException("Can only configure our own instance !");
        }
        switch (ignoreCase ? name.toLowerCase() : name) {
            case "resultqname":
            case "resultQName":
                setResultQName(PropertyConfigurerSupport.property(camelContext, QName.class, value));
                return true;
            case "documenttype":
            case "documentType":
                setDocumentType(PropertyConfigurerSupport.property(camelContext, Class.class, value));
                return true;
            case "xpathfactory":
            case "xpathFactory":
                setXpathFactory(PropertyConfigurerSupport.property(camelContext, XPathFactory.class, value));
                return true;
            case "saxon":
                setSaxon(PropertyConfigurerSupport.property(camelContext, Boolean.class, value));
                return true;
            case "objectmodeluri":
            case "objectModelUri":
                setObjectModelUri(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "threadsafety":
            case "threadSafety":
                setThreadSafety(PropertyConfigurerSupport.property(camelContext, Boolean.class, value));
                return true;
            case "lognamespaces":
            case "logNamespaces":
                setLogNamespaces(PropertyConfigurerSupport.property(camelContext, Boolean.class, value));
                return true;
            case "preCompile":
            case "precompile":
                setPreCompile(PropertyConfigurerSupport.property(camelContext, Boolean.class, value));
                return true;
            default:
                return false;
        }
    }

}
