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
package org.apache.camel.language.xpath;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathFactory;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.builder.xml.XPathBuilder;
import org.apache.camel.support.LanguageSupport;

/**
 * XPath language.
 */
public class XPathLanguage extends LanguageSupport {
    private QName resultType;
    private XPathFactory xpathFactory;
    private Boolean useSaxon;
    private String objectModelUri;
    private Boolean threadSafety;

    public Predicate createPredicate(String expression) {
        expression = loadResource(expression);

        XPathBuilder builder = XPathBuilder.xpath(expression);
        configureBuilder(builder);
        return builder;
    }

    public Expression createExpression(String expression) {
        expression = loadResource(expression);

        XPathBuilder builder = XPathBuilder.xpath(expression);
        configureBuilder(builder);
        return builder;
    }

    public QName getResultType() {
        return resultType;
    }

    public void setResultType(QName resultType) {
        this.resultType = resultType;
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

    public Boolean isUseSaxon() {
        return useSaxon != null && useSaxon;
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

    protected void configureBuilder(XPathBuilder builder) {
        if (threadSafety != null) {
            builder.setThreadSafety(threadSafety);
        }
        if (resultType != null) {
            builder.setResultQName(resultType);
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

    public boolean isSingleton() {
        return false;
    }
}
