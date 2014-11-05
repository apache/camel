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
package org.apache.camel.component.xmlsecurity.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.spec.ExcC14NParameterSpec;
import javax.xml.crypto.dsig.spec.XPathFilter2ParameterSpec;
import javax.xml.crypto.dsig.spec.XPathFilterParameterSpec;
import javax.xml.crypto.dsig.spec.XPathType;
import javax.xml.crypto.dsig.spec.XSLTTransformParameterSpec;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;

import org.apache.camel.util.IOHelper;

/**
 * Helps to construct the transformations and the canonicalization methods for
 * the XML Signature generator.
 */
public final class XmlSignatureHelper {
    private XmlSignatureHelper() {
        // Helper class
    }

    /**
     * Returns a configuration for a canonicalization algorithm.
     * 
     * @param algorithm
     *            algorithm URI
     * @return canonicalization
     * @throws IllegalArgumentException
     *             if <tt>algorithm</tt> is <code>null</code>
     */
    public static AlgorithmMethod getCanonicalizationMethod(String algorithm) {
        return getCanonicalizationMethod(algorithm, null);
    }

    /**
     * Returns a configuration for a canonicalization algorithm.
     * 
     * @param algorithm
     *            algorithm URI
     * @param inclusiveNamespacePrefixes
     *            namespace prefixes which should be treated like in the
     *            inclusive canonicalization, only relevant if the algorithm is
     *            exclusive
     * @return canonicalization
     * @throws IllegalArgumentException
     *             if <tt>algorithm</tt> is <code>null</code>
     */
    public static AlgorithmMethod getCanonicalizationMethod(String algorithm, List<String> inclusiveNamespacePrefixes) {
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm is null");
        }
        XmlSignatureTransform canonicalizationMethod = new XmlSignatureTransform(algorithm);
        if (inclusiveNamespacePrefixes != null) {
            ExcC14NParameterSpec parameters = new ExcC14NParameterSpec(inclusiveNamespacePrefixes);
            canonicalizationMethod.setParameterSpec(parameters);
        }
        return canonicalizationMethod;
    }

    public static AlgorithmMethod getEnvelopedTransform() {
        return new XmlSignatureTransform(Transform.ENVELOPED);
    }

    /**
     * Returns a configuration for a base64 transformation.
     * 
     * @return Base64 transformation
     */
    public static AlgorithmMethod getBase64Transform() {
        return new XmlSignatureTransform(Transform.BASE64);
    }

    /**
     * Returns a configuration for an XPATH transformation.
     * 
     * @param xpath
     *            XPATH expression
     * @return XPATH transformation
     * @throws IllegalArgumentException
     *             if <tt>xpath</tt> is <code>null</code>
     */
    public static AlgorithmMethod getXPathTransform(String xpath) {
        return getXPathTransform(xpath, null);
    }

    /**
     * Returns a configuration for an XPATH transformation which needs a
     * namespace map.
     * 
     * @param xpath
     *            XPATH expression
     * @param namespaceMap
     *            namespace map, key is the prefix, value is the namespace, can
     *            be <code>null</code>
     * @throws IllegalArgumentException
     *             if <tt>xpath</tt> is <code>null</code>
     * @return XPATH transformation
     */
    public static AlgorithmMethod getXPathTransform(String xpath, Map<String, String> namespaceMap) {
        if (xpath == null) {
            throw new IllegalArgumentException("xpath is null");
        }
        XmlSignatureTransform transformXPath = new XmlSignatureTransform();
        transformXPath.setAlgorithm(Transform.XPATH);
        XPathFilterParameterSpec params = getXpathFilter(xpath, namespaceMap);
        transformXPath.setParameterSpec(params);
        return transformXPath;
    }

    public static XPathFilterParameterSpec getXpathFilter(String xpath, Map<String, String> namespaceMap) {
        XPathFilterParameterSpec params = namespaceMap == null ? new XPathFilterParameterSpec(xpath) : new XPathFilterParameterSpec(xpath,
                namespaceMap);
        return params;
    }

    public static XPathFilterParameterSpec getXpathFilter(String xpath) {
        return getXpathFilter(xpath, null);
    }

    @SuppressWarnings("unchecked")
    public static XPathExpression getXPathExpression(XPathFilterParameterSpec xpathFilter) throws XPathExpressionException {

        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();
        if (xpathFilter.getNamespaceMap() != null) {
            xpath.setNamespaceContext(new XPathNamespaceContext(xpathFilter.getNamespaceMap()));
        }
        return xpath.compile(xpathFilter.getXPath());
    }

    private static class XPathNamespaceContext implements NamespaceContext {

        private final Map<String, String> prefix2Namespace;

        XPathNamespaceContext(Map<String, String> prefix2Namespace) {
            this.prefix2Namespace = prefix2Namespace;
        }

        public String getNamespaceURI(String prefix) {
            if (prefix == null) {
                throw new NullPointerException("Null prefix");
            }
            if ("xml".equals(prefix)) {
                return XMLConstants.XML_NS_URI;
            }
            String ns = prefix2Namespace.get(prefix);
            if (ns != null) {
                return ns;
            }
            return XMLConstants.NULL_NS_URI;
        }

        // This method isn't necessary for XPath processing.
        public String getPrefix(String uri) {
            throw new UnsupportedOperationException();
        }

        // This method isn't necessary for XPath processing either.
        @SuppressWarnings("rawtypes")
        public Iterator getPrefixes(String uri) {
            throw new UnsupportedOperationException();
        }

    }

    /**
     * Returns a configuration for an XPATH2 transformation.
     * 
     * @param xpath
     *            XPATH expression
     * @param filter
     *            possible values are "intersect", "subtract", "union"
     * @throws IllegalArgumentException
     *             if <tt>xpath</tt> or <tt>filter</tt> is <code>null</code>, or
     *             is neither "intersect", nor "subtract", nor "union"
     * @return XPATH transformation
     */
    public static AlgorithmMethod getXPath2Transform(String xpath, String filter) {
        return getXPath2Transform(xpath, filter, null);
    }

    /**
     * Returns a configuration for an XPATH2 transformation which consists of
     * several XPATH expressions.
     * 
     * @param xpathAndFilterList
     *            list of XPATH expressions with their filters
     * @param namespaceMap
     *            namespace map, key is the prefix, value is the namespace, can
     *            be <code>null</code>
     * @throws IllegalArgumentException
     *             if <tt>xpathAndFilterList</tt> is <code>null</code> or empty,
     *             or the specified filter values are neither "intersect", nor
     *             "subtract", nor "union"
     * @return XPATH transformation
     */
    public static AlgorithmMethod getXPath2Transform(String xpath, String filter, Map<String, String> namespaceMap) {
        XPathAndFilter xpathAndFilter = new XPathAndFilter();
        xpathAndFilter.setXpath(xpath);
        xpathAndFilter.setFilter(filter);
        List<XPathAndFilter> list = new ArrayList<XmlSignatureHelper.XPathAndFilter>(1);
        list.add(xpathAndFilter);
        return getXPath2Transform(list, namespaceMap);
    }

    /**
     * Returns a configuration for an XPATH2 transformation which consists of
     * several XPATH expressions.
     * 
     * @param xpathAndFilterList
     *            list of XPATH expressions with their filters
     * @param namespaceMap
     *            namespace map, key is the prefix, value is the namespace, can
     *            be <code>null</code>
     * @throws IllegalArgumentException
     *             if <tt>xpathAndFilterList</tt> is <code>null</code> or empty,
     *             or the specified filter values are neither "intersect", nor
     *             "subtract", nor "union"
     * @return XPATH transformation
     */
    public static AlgorithmMethod getXPath2Transform(List<XPathAndFilter> xpathAndFilterList, Map<String, String> namespaceMap) {
        if (xpathAndFilterList == null) {
            throw new IllegalArgumentException("xpathAndFilterList is null");
        }
        if (xpathAndFilterList.isEmpty()) {
            throw new IllegalArgumentException("XPath and filter list is empty");
        }
        List<XPathType> list = getXPathTypeList(xpathAndFilterList, namespaceMap);
        XmlSignatureTransform transformXPath = new XmlSignatureTransform(Transform.XPATH2);
        transformXPath.setParameterSpec(new XPathFilter2ParameterSpec(list));
        return transformXPath;
    }

    private static List<XPathType> getXPathTypeList(List<XPathAndFilter> xpathAndFilterList, Map<String, String> namespaceMap) {
        List<XPathType> list = new ArrayList<XPathType>(xpathAndFilterList.size());
        for (XPathAndFilter xpathAndFilter : xpathAndFilterList) {
            XPathType.Filter xpathFilter;
            if (XPathType.Filter.INTERSECT.toString().equals(xpathAndFilter.getFilter())) {
                xpathFilter = XPathType.Filter.INTERSECT;
            } else if (XPathType.Filter.SUBTRACT.toString().equals(xpathAndFilter.getFilter())) {
                xpathFilter = XPathType.Filter.SUBTRACT;
            } else if (XPathType.Filter.UNION.toString().equals(xpathAndFilter.getFilter())) {
                xpathFilter = XPathType.Filter.UNION;
            } else {
                throw new IllegalStateException(String.format("XPATH %s has a filter %s not supported", xpathAndFilter.getXpath(),
                        xpathAndFilter.getFilter()));
            }

            XPathType xpathtype = namespaceMap == null ? new XPathType(xpathAndFilter.getXpath(), xpathFilter) : new XPathType(
                    xpathAndFilter.getXpath(), xpathFilter, namespaceMap);
            list.add(xpathtype);
        }
        return list;
    }

    /**
     * Returns a configuration for an XPATH2 transformation which consists of
     * several XPATH expressions.
     * 
     * @param xpathAndFilterList
     *            list of XPATH expressions with their filters
     * @throws IllegalArgumentException
     *             if <tt>xpathAndFilterList</tt> is <code>null</code> or empty,
     *             or the specified filte values are neither "intersect", nor
     *             "subtract", nor "union"
     * @return XPATH transformation
     */
    public static AlgorithmMethod getXPath2Transform(List<XPathAndFilter> xpathAndFilterList) {
        return getXPath2Transform(xpathAndFilterList, null);
    }

    /**
     * Returns a configuration for an XSL transformation.
     * 
     * @param path
     *            path to the XSL file in the classpath
     * @return XSL transform
     * @throws IllegalArgumentException
     *             if <tt>path</tt> is <code>null</code>
     * @throws IllegalStateException
     *             if the XSL file cannot be found
     * @throws Exception
     *             if an error during the reading of the XSL file occurs
     */
    public static AlgorithmMethod getXslTransform(String path) throws Exception { //NOPMD
        InputStream is = readXslTransform(path);
        if (is == null) {
            throw new IllegalStateException(String.format("XSL file %s not found", path));
        }
        try {
            return getXslTranform(is);
        } finally {
            IOHelper.close(is);
        }
    }

    /**
     * Returns a configuration for an XSL transformation.
     * 
     * @param is
     *            input stream of the XSL
     * @return XSL transform
     * @throws IllegalArgumentException
     *             if <tt>is</tt> is <code>null</code>
     * @throws Exception
     *             if an error during the reading of the XSL file occurs
     */
    public static AlgorithmMethod getXslTranform(InputStream is) throws SAXException, IOException, ParserConfigurationException {
        if (is == null) {
            throw new IllegalArgumentException("is must not be null");
        }
        Document doc = parseInput(is);
        DOMStructure stylesheet = new DOMStructure(doc.getDocumentElement());
        XSLTTransformParameterSpec spec = new XSLTTransformParameterSpec(stylesheet);
        XmlSignatureTransform transformXslt = new XmlSignatureTransform();
        transformXslt.setAlgorithm(Transform.XSLT);
        transformXslt.setParameterSpec(spec);
        return transformXslt;
    }

    protected static InputStream readXslTransform(String path) throws Exception { //NOPMD
        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }
        return XmlSignatureHelper.class.getResourceAsStream(path);
    }

    public static List<AlgorithmMethod> getTransforms(List<AlgorithmMethod> list) {
        return list;
    }

    private static Document parseInput(InputStream is) throws SAXException, IOException, ParserConfigurationException {
        return newDocumentBuilder(Boolean.TRUE).parse(is);
    }

    public static List<Node> getTextAndElementChildren(Node node) {
        List<Node> result = new LinkedList<Node>();
        NodeList children = node.getChildNodes();
        if (children == null) {
            return result;
        }
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (Node.ELEMENT_NODE == child.getNodeType() || Node.TEXT_NODE == child.getNodeType()) {
                result.add(child);
            }
        }
        return result;
    }

    public static DocumentBuilder newDocumentBuilder(Boolean disallowDoctypeDecl) throws ParserConfigurationException {
        return newDocumentBuilder(disallowDoctypeDecl, null);
    }

    public static DocumentBuilder newDocumentBuilder(Boolean disallowDoctypeDecl, Schema schema) throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setValidating(false);
        // avoid external entity attacks
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        boolean isDissalowDoctypeDecl = disallowDoctypeDecl == null ? true : disallowDoctypeDecl;
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", isDissalowDoctypeDecl);
        // avoid overflow attacks
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        if (schema != null) {
            dbf.setSchema(schema);
        }

        return dbf.newDocumentBuilder();
    }

    public static void transformToOutputStream(Node node, OutputStream os, boolean omitXmlDeclaration, String encoding) throws Exception { //NOPMD

        if (node.getNodeType() == Node.TEXT_NODE) {
            byte[] bytes = tranformTextNodeToByteArray(node, encoding);
            os.write(bytes);
        } else {
            transformNonTextNodeToOutputStream(node, os, omitXmlDeclaration, encoding);
        }
    }

    /**
     * Use {@link #transformToOutputStream(Node, OutputStream, boolean, String)}
     * instead.
     */
    @Deprecated
    public static void transformToOutputStream(Node node, OutputStream os, boolean omitXmlDeclaration) throws Exception { //NOPMD

        if (node.getNodeType() == Node.TEXT_NODE) {
            byte[] bytes = tranformTextNodeToByteArray(node);
            os.write(bytes);
        } else {
            transformNonTextNodeToOutputStream(node, os, omitXmlDeclaration);
        }
    }

    /**
     * Use
     * {@link #transformNonTextNodeToOutputStream(Node, OutputStream, boolean, String)}
     * instead.
     */
    @Deprecated
    public static void transformNonTextNodeToOutputStream(Node node, OutputStream os, boolean omitXmlDeclaration) throws Exception { //NOPMD
        transformNonTextNodeToOutputStream(node, os, omitXmlDeclaration, null);
    }

    /**
     * Serializes a node using a certain character encoding.
     * 
     * @param node
     *            DOM node to serialize
     * @param os
     *            output stream, to which the node is serialized
     * @param omitXmlDeclaration
     *            indicator whether to omit the XML declaration or not
     * @param encoding
     *            character encoding, can be <code>null</code>, if
     *            <code>null</code> then "UTF-8" is used
     * @throws Exception
     */
    public static void transformNonTextNodeToOutputStream(Node node, OutputStream os, boolean omitXmlDeclaration, String encoding)
        throws Exception { //NOPMD
        // previously we used javax.xml.transform.Transformer, however the JDK xalan implementation did not work correctly with a specified encoding
        // therefore we switched to DOMImplementationLS
        if (encoding == null) {
            encoding = "UTF-8";
        }
        DOMImplementationRegistry domImplementationRegistry = DOMImplementationRegistry.newInstance();
        DOMImplementationLS domImplementationLS = (DOMImplementationLS) domImplementationRegistry.getDOMImplementation("LS");
        LSOutput lsOutput = domImplementationLS.createLSOutput();
        lsOutput.setEncoding(encoding);
        lsOutput.setByteStream(os);
        LSSerializer lss = domImplementationLS.createLSSerializer();
        lss.getDomConfig().setParameter("xml-declaration", !omitXmlDeclaration);
        lss.write(node, lsOutput);
    }

    /** use {@link #tranformTextNodeToByteArray(Node, String)} instead. */
    @Deprecated
    public static byte[] tranformTextNodeToByteArray(Node node) {
        return tranformTextNodeToByteArray(node, null);
    }

    /**
     * Trannsforms a text node to byte array using a certain character encoding.
     * 
     * @param node
     *            text node
     * @param encoding
     *            character encoding, can be <code>null</code>, if
     *            <code>null</code> then UTF-8 is used
     * @return byte array, <code>null</code> if the node has not text content
     * @throws IllegalStateException
     *             if the encoding is not supported
     */
    public static byte[] tranformTextNodeToByteArray(Node node, String encoding) {
        if (encoding == null) {
            encoding = "UTF-8";
        }
        String text = node.getTextContent();
        if (text != null) {
            try {
                return text.getBytes(encoding);
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        } else {
            return null;
        }
    }

    public static Document getDocument(Node node) {
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            return (Document) node;
        }
        return node.getOwnerDocument();
    }

    public static class XPathAndFilter {

        private String xpath;

        private String filter;

        public XPathAndFilter(String xpath, String filter) {
            this.xpath = xpath;
            this.filter = filter;
        }

        public XPathAndFilter() {

        }

        public String getXpath() {
            return xpath;
        }

        public void setXpath(String xpath) {
            this.xpath = xpath;
        }

        public String getFilter() {
            return filter;
        }

        public void setFilter(String filter) {
            this.filter = filter;
        }

    }

}
