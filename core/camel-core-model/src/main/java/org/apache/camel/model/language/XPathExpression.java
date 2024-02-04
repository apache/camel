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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

// TODO: camel4 (need jakarta api)
import javax.xml.xpath.XPathFactory;

import org.apache.camel.Expression;
import org.apache.camel.spi.Metadata;

/**
 * Evaluates an XPath expression against an XML payload.
 */
@Metadata(firstVersion = "1.1.0", label = "language,core,xml", title = "XPath")
@XmlRootElement(name = "xpath")
@XmlAccessorType(XmlAccessType.FIELD)
public class XPathExpression extends NamespaceAwareExpression {

    @XmlTransient
    private Class<?> documentType;
    @XmlTransient
    private XPathFactory xpathFactory;

    @XmlAttribute(name = "documentType")
    @Metadata(label = "advanced")
    private String documentTypeName;
    @XmlAttribute(name = "resultQName")
    @Metadata(defaultValue = "NODESET", enums = "NUMBER,STRING,BOOLEAN,NODESET,NODE")
    private String resultQName;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String saxon;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String factoryRef;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String objectModel;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String logNamespaces;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String threadSafety;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean", defaultValue = "true")
    private String preCompile;

    public XPathExpression() {
    }

    public XPathExpression(String expression) {
        super(expression);
    }

    public XPathExpression(Expression expression) {
        setExpressionValue(expression);
    }

    private XPathExpression(Builder builder) {
        super(builder);
        this.documentType = builder.documentType;
        this.xpathFactory = builder.xpathFactory;
        this.documentTypeName = builder.documentTypeName;
        this.resultQName = builder.resultQName;
        this.saxon = builder.saxon;
        this.factoryRef = builder.factoryRef;
        this.objectModel = builder.objectModel;
        this.logNamespaces = builder.logNamespaces;
        this.threadSafety = builder.threadSafety;
        this.preCompile = builder.preCompile;
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

    public String getResultQName() {
        return resultQName;
    }

    /**
     * Sets the output type supported by XPath.
     */
    public void setResultQName(String resultQName) {
        this.resultQName = resultQName;
    }

    /**
     * Whether to use Saxon.
     */
    public void setSaxon(String saxon) {
        this.saxon = saxon;
    }

    public String getSaxon() {
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
     * Whether to log namespaces which can assist during troubleshooting
     */
    public void setLogNamespaces(String logNamespaces) {
        this.logNamespaces = logNamespaces;
    }

    public String getLogNamespaces() {
        return logNamespaces;
    }

    public XPathFactory getXPathFactory() {
        return xpathFactory;
    }

    public void setXPathFactory(XPathFactory xpathFactory) {
        this.xpathFactory = xpathFactory;
    }

    public String getThreadSafety() {
        return threadSafety;
    }

    /**
     * Whether to enable thread-safety for the returned result of the xpath expression. This applies to when using
     * NODESET as the result type, and the returned set has multiple elements. In this situation there can be
     * thread-safety issues if you process the NODESET concurrently such as from a Camel Splitter EIP in parallel
     * processing mode. This option prevents concurrency issues by doing defensive copies of the nodes.
     * <p/>
     * It is recommended to turn this option on if you are using camel-saxon or Saxon in your application. Saxon has
     * thread-safety issues which can be prevented by turning this option on.
     */
    public void setThreadSafety(String threadSafety) {
        this.threadSafety = threadSafety;
    }

    public String getPreCompile() {
        return preCompile;
    }

    /**
     * Whether to enable pre-compiling the xpath expression during initialization phase. pre-compile is enabled by
     * default.
     * <p>
     * This can be used to turn off, for example in cases the compilation phase is desired at the starting phase, such
     * as if the application is ahead of time compiled (for example with camel-quarkus) which would then load the xpath
     * factory of the built operating system, and not a JVM runtime.
     */
    public void setPreCompile(String preCompile) {
        this.preCompile = preCompile;
    }

    /**
     * {@code Builder} is a specific builder for {@link XPathExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractNamespaceAwareBuilder<Builder, XPathExpression> {

        private Class<?> documentType;
        private XPathFactory xpathFactory;
        private String documentTypeName;
        private String resultQName;
        private String saxon;
        private String factoryRef;
        private String objectModel;
        private String logNamespaces;
        private String threadSafety;
        private String preCompile;

        /**
         * Class for document type to use
         * <p/>
         * The default value is org.w3c.dom.Document
         */
        public Builder documentType(Class<?> documentType) {
            this.documentType = documentType;
            return this;
        }

        public Builder xpathFactory(XPathFactory xpathFactory) {
            this.xpathFactory = xpathFactory;
            return this;
        }

        /**
         * Name of class for document type
         * <p/>
         * The default value is org.w3c.dom.Document
         */
        public Builder documentTypeName(String documentTypeName) {
            this.documentTypeName = documentTypeName;
            return this;
        }

        /**
         * Sets the class name of the result type (type from output)
         * <p/>
         * The default result type is NodeSet
         */
        public Builder resultQName(String resultTypeName) {
            this.resultQName = resultQName;
            return this;
        }

        /**
         * Whether to use Saxon.
         */
        public Builder saxon(String saxon) {
            this.saxon = saxon;
            return this;
        }

        /**
         * Whether to use Saxon.
         */
        public Builder saxon(boolean saxon) {
            this.saxon = Boolean.toString(saxon);
            return this;
        }

        /**
         * References to a custom XPathFactory to lookup in the registry
         */
        public Builder factoryRef(String factoryRef) {
            this.factoryRef = factoryRef;
            return this;
        }

        /**
         * The XPath object model to use
         */
        public Builder objectModel(String objectModel) {
            this.objectModel = objectModel;
            return this;
        }

        /**
         * Whether to log namespaces which can assist during troubleshooting
         */
        public Builder logNamespaces(String logNamespaces) {
            this.logNamespaces = logNamespaces;
            return this;
        }

        /**
         * Whether to log namespaces which can assist during troubleshooting
         */
        public Builder logNamespaces(boolean logNamespaces) {
            this.logNamespaces = Boolean.toString(logNamespaces);
            return this;
        }

        /**
         * Whether to enable thread-safety for the returned result of the xpath expression. This applies to when using
         * NODESET as the result type, and the returned set has multiple elements. In this situation there can be
         * thread-safety issues if you process the NODESET concurrently such as from a Camel Splitter EIP in parallel
         * processing mode. This option prevents concurrency issues by doing defensive copies of the nodes.
         * <p/>
         * It is recommended to turn this option on if you are using camel-saxon or Saxon in your application. Saxon has
         * thread-safety issues which can be prevented by turning this option on.
         */
        public Builder threadSafety(String threadSafety) {
            this.threadSafety = threadSafety;
            return this;
        }

        /**
         * Whether to enable thread-safety for the returned result of the xpath expression. This applies to when using
         * NODESET as the result type, and the returned set has multiple elements. In this situation there can be
         * thread-safety issues if you process the NODESET concurrently such as from a Camel Splitter EIP in parallel
         * processing mode. This option prevents concurrency issues by doing defensive copies of the nodes.
         * <p/>
         * It is recommended to turn this option on if you are using camel-saxon or Saxon in your application. Saxon has
         * thread-safety issues which can be prevented by turning this option on.
         */
        public Builder threadSafety(boolean threadSafety) {
            this.threadSafety = Boolean.toString(threadSafety);
            return this;
        }

        /**
         * Whether to enable pre-compiling the xpath expression during initialization phase. pre-compile is enabled by
         * default.
         * <p>
         * This can be used to turn off, for example in cases the compilation phase is desired at the starting phase,
         * such as if the application is ahead of time compiled (for example with camel-quarkus) which would then load
         * the xpath factory of the built operating system, and not a JVM runtime.
         */
        public Builder preCompile(String preCompile) {
            this.preCompile = preCompile;
            return this;
        }

        /**
         * Whether to enable pre-compiling the xpath expression during initialization phase. pre-compile is enabled by
         * default.
         * <p>
         * This can be used to turn off, for example in cases the compilation phase is desired at the starting phase,
         * such as if the application is ahead of time compiled (for example with camel-quarkus) which would then load
         * the xpath factory of the built operating system, and not a JVM runtime.
         */
        public Builder preCompile(boolean preCompile) {
            this.preCompile = Boolean.toString(preCompile);
            return this;
        }

        @Override
        public XPathExpression end() {
            return new XPathExpression(this);
        }
    }
}
