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
package org.apache.camel.model.language;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.xpath.XPathFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

/**
 * To use XPath (XML) in Camel expressions or predicates.
 */
@Metadata(firstVersion = "1.1.0", label = "language,core,xml", title = "XPath")
@XmlRootElement(name = "xpath")
@XmlAccessorType(XmlAccessType.FIELD)
public class XPathExpression extends NamespaceAwareExpression {

    @XmlAttribute(name = "documentType")
    private String documentTypeName;
    @XmlAttribute(name = "resultType") @Metadata(defaultValue = "NODESET", enums = "NUMBER,STRING,BOOLEAN,NODESET,NODE")
    private String resultTypeName;
    @XmlAttribute
    private Boolean saxon;
    @XmlAttribute @Metadata(label = "advanced")
    private String factoryRef;
    @XmlAttribute @Metadata(label = "advanced")
    private String objectModel;
    @XmlAttribute
    private Boolean logNamespaces;
    @XmlAttribute
    private String headerName;
    @XmlTransient
    private Class<?> documentType;
    @XmlTransient
    private Class<?> resultType;
    @XmlTransient
    private XPathFactory xpathFactory;
    @XmlAttribute @Metadata(label = "advanced")
    private Boolean threadSafety;

    public XPathExpression() {
    }

    public XPathExpression(String expression) {
        super(expression);
    }

    public XPathExpression(Expression expression) {
        setExpressionValue(expression);
    }

    @Override
    public String getLanguage() {
        return "xpath";
    }

    public Class<?> getDocumentType() {
        return documentType;
    }

    /**
     * Class for document type to use
     * <p/>
     * The default value is org.w3c.dom.Document
     */
    public void setDocumentType(Class<?> documentType) {
        this.documentType = documentType;
    }

    public String getDocumentTypeName() {
        return documentTypeName;
    }

    /**
     * Name of class for document type
     * <p/>
     * The default value is org.w3c.dom.Document
     */
    public void setDocumentTypeName(String documentTypeName) {
        this.documentTypeName = documentTypeName;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    /**
     * Sets the class of the result type (type from output).
     * <p/>
     * The default result type is NodeSet
     */
    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public String getResultTypeName() {
        return resultTypeName;
    }

    /**
     * Sets the class name of the result type (type from output)
     * <p/>
     * The default result type is NodeSet
     */
    public void setResultTypeName(String resultTypeName) {
        this.resultTypeName = resultTypeName;
    }

    /**
     * Whether to use Saxon.
     */
    public void setSaxon(Boolean saxon) {
        this.saxon = saxon;
    }

    public Boolean getSaxon() {
        return saxon;
    }

    /**
     * References to a custom XPathFactory to lookup in the registry
     */
    public void setFactoryRef(String factoryRef) {
        this.factoryRef = factoryRef;
    }

    public String getFactoryRef() {
        return factoryRef;
    }

    /**
     * The XPath object model to use
     */
    public void setObjectModel(String objectModel) {
        this.objectModel = objectModel;
    }

    public String getObjectModel() {
        return objectModel;
    }

    /**
     * Whether to log namespaces which can assist during trouble shooting
     */
    public void setLogNamespaces(Boolean logNamespaces) {
        this.logNamespaces = logNamespaces;
    }

    public Boolean getLogNamespaces() {
        return logNamespaces;
    }

    public String getHeaderName() {
        return headerName;
    }

    /**
     * Name of header to use as input, instead of the message body
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public Boolean getThreadSafety() {
        return threadSafety;
    }

    /**
     * Whether to enable thread-safety for the returned result of the xpath expression.
     * This applies to when using NODESET as the result type, and the returned set has
     * multiple elements. In this situation there can be thread-safety issues if you
     * process the NODESET concurrently such as from a Camel Splitter EIP in parallel processing mode.
     * This option prevents concurrency issues by doing defensive copies of the nodes.
     * <p/>
     * It is recommended to turn this option on if you are using camel-saxon or Saxon in your application.
     * Saxon has thread-safety issues which can be prevented by turning this option on.
     */
    public void setThreadSafety(Boolean threadSafety) {
        this.threadSafety = threadSafety;
    }

    @Override
    public Expression createExpression(CamelContext camelContext) {
        if (documentType == null && documentTypeName != null) {
            try {
                documentType = camelContext.getClassResolver().resolveMandatoryClass(documentTypeName);
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
        if (resultType == null && resultTypeName != null) {
            try {
                resultType = camelContext.getClassResolver().resolveMandatoryClass(resultTypeName);
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
        resolveXPathFactory(camelContext);
        return super.createExpression(camelContext);
    }

    @Override
    public Predicate createPredicate(CamelContext camelContext) {
        if (documentType == null && documentTypeName != null) {
            try {
                documentType = camelContext.getClassResolver().resolveMandatoryClass(documentTypeName);
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
        resolveXPathFactory(camelContext);
        return super.createPredicate(camelContext);
    }

    @Override
    protected void configureExpression(CamelContext camelContext, Expression expression) {
        boolean isSaxon = getSaxon() != null && getSaxon();
        boolean isLogNamespaces = getLogNamespaces() != null && getLogNamespaces();

        if (documentType != null) {
            setProperty(camelContext, expression, "documentType", documentType);
        }
        if (resultType != null) {
            setProperty(camelContext, expression, "resultType", resultType);
        }
        if (isSaxon) {
            setProperty(camelContext, expression, "useSaxon", true);
        }
        if (xpathFactory != null) {
            setProperty(camelContext, expression, "xPathFactory", xpathFactory);
        }
        if (objectModel != null) {
            setProperty(camelContext, expression, "objectModelUri", objectModel);
        }
        if (threadSafety != null) {
            setProperty(camelContext, expression, "threadSafety", threadSafety);
        }
        if (isLogNamespaces) {
            setProperty(camelContext, expression, "logNamespaces", true);
        }
        if (ObjectHelper.isNotEmpty(getHeaderName())) {
            setProperty(camelContext, expression, "headerName", getHeaderName());
        }
        // moved the super configuration to the bottom so that the namespace init picks up the newly set XPath Factory
        super.configureExpression(camelContext, expression);
    }

    @Override
    protected void configurePredicate(CamelContext camelContext, Predicate predicate) {
        boolean isSaxon = getSaxon() != null && getSaxon();
        boolean isLogNamespaces = getLogNamespaces() != null && getLogNamespaces();

        if (documentType != null) {
            setProperty(camelContext, predicate, "documentType", documentType);
        }
        if (resultType != null) {
            setProperty(camelContext, predicate, "resultType", resultType);
        }
        if (isSaxon) {
            setProperty(camelContext, predicate, "useSaxon", true);
        }
        if (xpathFactory != null) {
            setProperty(camelContext, predicate, "xPathFactory", xpathFactory);
        }
        if (objectModel != null) {
            setProperty(camelContext, predicate, "objectModelUri", objectModel);
        }
        if (threadSafety != null) {
            setProperty(camelContext, predicate, "threadSafety", threadSafety);
        }
        if (isLogNamespaces) {
            setProperty(camelContext, predicate, "logNamespaces", true);
        }
        if (ObjectHelper.isNotEmpty(getHeaderName())) {
            setProperty(camelContext, predicate, "headerName", getHeaderName());
        }
        // moved the super configuration to the bottom so that the namespace init picks up the newly set XPath Factory
        super.configurePredicate(camelContext, predicate);
    }

    private void resolveXPathFactory(CamelContext camelContext) {
        // Factory and Object Model can be set simultaneously. The underlying XPathBuilder allows for setting Saxon too, as it is simply a shortcut for
        // setting the appropriate Object Model, it is not wise to allow this in XML because the order of invocation of the setters by JAXB may cause undeterministic behaviour 
        if ((ObjectHelper.isNotEmpty(factoryRef) || ObjectHelper.isNotEmpty(objectModel)) && (saxon != null)) {
            throw new IllegalArgumentException("The saxon attribute cannot be set on the xpath element if any of the following is also set: factory, objectModel" + this);
        }

        // Validate the factory class
        if (ObjectHelper.isNotEmpty(factoryRef)) {
            xpathFactory = camelContext.getRegistry().lookupByNameAndType(factoryRef, XPathFactory.class);
            if (xpathFactory == null) {
                throw new IllegalArgumentException("The provided XPath Factory is invalid; either it cannot be resolved or it is not an XPathFactory instance");
            }
        }
    }
}
