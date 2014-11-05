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

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.Manifest;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.XMLObject;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.spec.XPathFilterParameterSpec;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Maps the XML signature to a camel message. A output node is determined from
 * the XML signature document via a node search and then serialized and set to
 * the output message body.
 * <p>
 * There are three output node search types supported: "Default", "ElementName",
 * and "XPath". All these search types support enveloped XML signature or
 * enveloping XML signature.
 * <p>
 * <ul>
 * <li>The "ElementName" search uses the local name and namespace specified in
 * the search value to determine the output element from the XML signature
 * document. With the input parameter 'RemoveSignatureElements", you can specify
 * whether the signature elements should be removed from the resulting output
 * document. This flag shall be used for enveloped XML signatures.
 * <li>The "XPath" search uses an XPath expression to evaluate the output node.
 * In this case the output node can be of type Element, TextNode, or Document.
 * With the input parameter 'RemoveSignatureElements", you can specify whether
 * the signature elements should be removed from the resulting output document.
 * This flag shall be used for enveloped XML signatures.
 * <li>The "Default" search is explained in more detail below.
 * </ul>
 * <p>
 * Default Output Node Search:
 * <ul>
 * In the enveloped XML signature case, the XML document without the signature
 * part is returned in the message body.
 * <p>
 * In the enveloping XML signature case, the message body is determined from a
 * referenced Object element in the following way:
 * <ul>
 * <li>Only same document references are taken into account (URI must start with
 * '#').
 * <li>Also indirect same document references to an object via manifest are
 * taken into account.
 * <li>The resulting number of object references must be 1.
 * <li>The referenced object must contain exactly 1 {@link DOMStructure}.
 * <li>The node of the DOMStructure is serialized to a byte array and added as
 * body to the message.
 * </ul>
 * This does mean that the enveloping XML signature must have either the
 * structure
 * 
 * <pre>
 *     {@code
 *     <Signature>
 *         <SignedInfo>
 *            <Reference URI="#object"/>       
 *            <!-- further references possible but they must not point to an Object or Manifest containing an object reference -->
 *            ...
 *         </SignedInfo>
 *     
 *         <Object Id="object">
 *              <!-- contains the DOM node which should be extracted to the message body -->
 *         <Object>
 *         <!-- further object elements possible which are not referenced-->
 *         ...
 *         (<KeyInfo>)?
 *     </Signature>
 *     }
 * </pre>
 * 
 * or the structure
 * 
 * <pre>
 *     {@code
 *     <Signature>
 *         <SignedInfo>
 *            <Reference URI="#manifest"/>       
 *            <!-- further references  are possible but they must not point to an Object or other manifest containing an object reference -->
 *            ...
 *         </SignedInfo>
 *     
 *         <Object >
 *            <Manifest Id="manifest">
 *               <Reference URI=#object/>
 *            </Manifest>
 *         </Objet>
 *         <Object Id="object">
 *             <!-- contains the DOM node which should be extracted to the message body -->
 *         </Object>
 *          <!-- further object elements possible which are not referenced -->
 *         ...
 *         (<KeyInfo>)?
 *     </Signature>
 *     }
 * </pre>
 * 
 * </ul>
 */
public class DefaultXmlSignature2Message implements XmlSignature2Message {

    /**
     * Search type 'Default' for determining the output node.
     * 
     */
    public static final String OUTPUT_NODE_SEARCH_TYPE_DEFAULT = "Default";

    /**
     * Search type 'ElementName' for determining the output element.
     * 
     */
    public static final String OUTPUT_NODE_SEARCH_TYPE_ELEMENT_NAME = "ElementName";

    /**
     * Search type 'XPath' for determining the output node. Search value must be
     * of type {@link XPathFilterParameterSpec}.
     * 
     */
    public static final String OUTPUT_NODE_SEARCH_TYPE_XPATH = "XPath";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultXmlSignature2Message.class);

    @Override
    public void mapToMessage(Input input, Message output) throws Exception { //NOPMD

        Node node;
        boolean removeSignatureElements = false;
        if (OUTPUT_NODE_SEARCH_TYPE_DEFAULT.equals(input.getOutputNodeSearchType())) {
            LOG.debug("Searching for output node via default search");
            if (isEnveloping(input)) {
                node = getNodeForMessageBodyInEnvelopingCase(input);
            } else {
                // enveloped or detached XML signature  --> remove signature element
                node = input.getMessageBodyDocument().getDocumentElement();
                removeSignatureElements = true;
            }
        } else if (OUTPUT_NODE_SEARCH_TYPE_ELEMENT_NAME.equals(input.getOutputNodeSearchType())) {
            node = getOutputElementViaLocalNameAndNamespace(input);
        } else if (OUTPUT_NODE_SEARCH_TYPE_XPATH.equals(input.getOutputNodeSearchType())) {
            node = getOutputNodeViaXPath(input);
        } else {
            throw new XmlSignatureException(String.format("Wrong configuration: The output node search type %s is not supported.",
                    input.getOutputNodeSearchType()));
        }

        LOG.debug("Output node with local name {} and namespace {} found", node.getLocalName(), node.getNamespaceURI());

        if (!removeSignatureElements) {
            removeSignatureElements = input.getRemoveSignatureElements() != null && input.getRemoveSignatureElements();
        }

        if (removeSignatureElements) {
            removeSignatureElements(node);
        }

        transformNodeToByteArrayAndSetToOutputMessage(input, output, node);
    }

    protected void transformNodeToByteArrayAndSetToOutputMessage(Input input, Message output, Node node)
        throws Exception {

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        XmlSignatureHelper.transformToOutputStream(node, os, omitXmlDeclaration(output, input), input.getOutputXmlEncoding());
        output.setBody(os.toByteArray());
        if (input.getOutputXmlEncoding() != null) {
            output.setHeader(Exchange.CHARSET_NAME, input.getOutputXmlEncoding());
        }
    }


    protected Node getOutputNodeViaXPath(Input input) throws Exception { //NOPMD
        checkSearchValueNotNull(input);
        checkSearchValueOfType(XPathFilterParameterSpec.class, input);
        XPathFilterParameterSpec xpathFilter = (XPathFilterParameterSpec) input.getOutputNodeSearch();
        XPathExpression expr = XmlSignatureHelper.getXPathExpression(xpathFilter);
        NodeList nodes = (NodeList) expr.evaluate(input.getMessageBodyDocument(), XPathConstants.NODESET);
        if (nodes == null || nodes.getLength() == 0) {
            throw new XmlSignatureException(
                    String.format(
                            "Cannot extract root node for the output document from the XML signature document. No node found for XPATH %s as specified in the output node search.",
                            xpathFilter.getXPath()));
        }
        if (nodes.getLength() > 1) {
            throw new XmlSignatureException(
                    String.format(
                            "Cannot extract root node for the output document from the XML signature document. XPATH %s as specified in the output node search results into more than one child.",
                            xpathFilter.getXPath()));

        }
        Node result = nodes.item(0);
        if (Node.ELEMENT_NODE == result.getNodeType() || Node.TEXT_NODE == result.getNodeType()
                || Node.DOCUMENT_NODE == result.getNodeType()) {
            return result;
        }
        throw new XmlSignatureException(String.format("Cannot extract root node for the output document from the XML signature document. "
                + "XPATH %s as specified in the output node search results into a node which has the wrong type.", xpathFilter.getXPath()));
    }

    protected Node getOutputElementViaLocalNameAndNamespace(Input input) throws Exception { //NOPMD
        String search = getNonEmptyStringSearchValue(input);
        String namespace;
        String localName;
        if ('{' == search.charAt(0)) {
            // namespace
            int index = search.indexOf('}');
            if (index < 1) {
                throw new XmlSignatureException(
                        String.format(
                                "Wrong configuration: Value %s for the output node search %s has wrong format. "
                                        + "Value must have the form '{<namespace>}<element local name>' or '<element local name>' if no the element has no namespace.",
                                search, input.getOutputNodeSearchType()));
            }
            namespace = search.substring(1, index);
            if (search.length() < index + 1) {
                throw new XmlSignatureException(
                        String.format(
                                "Wrong configuration: Value %s for the output node search %s has wrong format. "
                                        + "Value must have the form '{<namespace>}<element local name>' or '<element local name>' if no the element has no namespace.",
                                search, input.getOutputNodeSearchType()));
            }
            localName = search.substring(index + 1);
        } else {
            namespace = null;
            localName = search;
        }
        NodeList nodeList = input.getMessageBodyDocument().getElementsByTagNameNS(namespace, localName);
        if (nodeList.getLength() == 0) {
            throw new XmlSignatureException(
                    String.format(
                            "Cannot extract root element for the output document from the XML signature document. Element with local name %s and namespace %s does not exist.",
                            namespace, localName));
        }
        if (nodeList.getLength() > 1) {
            throw new XmlSignatureException(
                    String.format(
                            "Cannot extract root element for the output document from the XML signature document. More than one element found with local name %s and namespace %s.",
                            namespace, localName));
        }
        return nodeList.item(0);
    }

    protected String getNonEmptyStringSearchValue(Input input) throws Exception { //NOPMD
        checkSearchValueNotNull(input);
        checkSearchValueOfType(String.class, input);
        String search = (String) input.getOutputNodeSearch();
        checkStringSarchValueNotEmpty(search, input.getOutputNodeSearchType());
        return search;
    }

    protected void checkSearchValueOfType(Class<?> cl, Input input) throws Exception { //NOPMD
        if (!cl.isAssignableFrom(input.getOutputNodeSearch().getClass())) {
            throw new XMLSignatureException(String.format(
                    "Wrong configruation: Search value is of class %s, the output node search %s requires class %s.", input
                            .getOutputNodeSearch().getClass().getName(), input.getOutputNodeSearchType(), cl.getName()));
        }

    }

    protected void checkStringSarchValueNotEmpty(String searchValue, String outputNodeSearchType) throws Exception { //NOPMD
        if (searchValue.isEmpty()) {
            throw new XMLSignatureException(String.format("Wrong configruation: Value for output node search %s is empty.",
                    outputNodeSearchType));
        }
    }

    protected void checkSearchValueNotNull(Input input) throws Exception { //NOPMD
        LOG.debug("Searching for output element with search value '{}' and sarch type {}", input.getOutputNodeSearch(),
                input.getOutputNodeSearchType());
        if (input.getOutputNodeSearch() == null) {
            throw new XMLSignatureException(String.format("Wrong configruation: Value is missing for output node search %s.",
                    input.getOutputNodeSearchType()));
        }
    }

    protected Node getNodeForMessageBodyInEnvelopingCase(Input input) throws Exception { //NOPMD
        Node node;
        List<Reference> relevantReferences = getReferencesForMessageMapping(input);

        List<XMLObject> relevantObjects = getObjectsForMessageMapping(input);

        DOMStructure domStruc = getDomStructureForMessageBody(relevantReferences, relevantObjects);
        node = domStruc.getNode();
        return node;
    }

    /**
     * Removes the Signature elements from the document.
     * 
     * @param doc
     *            document
     */
    protected void removeSignatureElements(Node node) {
        Document doc = XmlSignatureHelper.getDocument(node);
        NodeList nl = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        List<Node> nodesToBeRemoved = new ArrayList<Node>(nl.getLength());
        for (int i = 0; i < nl.getLength(); i++) {
            // you cannot remove the nodes within this loop, because nl list would change
            nodesToBeRemoved.add(nl.item(i));
        }
        for (Node n : nodesToBeRemoved) {
            Node parent = n.getParentNode();
            if (parent != null) {
                parent.removeChild(n);
            }
        }
    }

    /**
     * Checks whether the XML document has as root element the signature
     * element.
     * 
     * @param input
     *            XML signature input
     * @return <code>true</code> if the root element of the xml signature
     *         document is the signature element; otherwise <code>false</code>
     * @throws Exception
     */
    protected boolean isEnveloping(Input input) throws Exception { //NOPMD
        Element el = input.getMessageBodyDocument().getDocumentElement();
        if ("Signature".equals(el.getLocalName()) && XMLSignature.XMLNS.equals(el.getNamespaceURI())) {
            return true;
        }
        return false;
    }

    protected Boolean omitXmlDeclaration(Message message, Input input) {
        Boolean omitXmlDeclaration = message.getHeader(XmlSignatureConstants.HEADER_OMIT_XML_DECLARATION, Boolean.class);
        if (omitXmlDeclaration == null) {
            omitXmlDeclaration = input.omitXmlDeclaration();
        }
        if (omitXmlDeclaration == null) {
            omitXmlDeclaration = Boolean.FALSE;
        }
        return omitXmlDeclaration;
    }

    /**
     * Returns the references whose referenced objects are taken into account
     * for the message body. This message you can use to filter the relevant
     * references from the references provided by the input parameter.
     * 
     * 
     * @param input
     *            references and objects
     * @return relevant references for the mapping to the camel message
     * @throws Exception
     *             if an error occurs
     */
    protected List<Reference> getReferencesForMessageMapping(Input input) throws Exception { //NOPMD
        return input.getReferences();
    }

    /**
     * Returns the objects which must be taken into account for the mapping to
     * the camel message.
     * 
     * @param input
     *            references and objects
     * @return relevant objects for the mapping to camel message
     * @throws Exception
     *             if an error occurs
     */
    protected List<XMLObject> getObjectsForMessageMapping(Input input) throws Exception { //NOPMD
        return input.getObjects();
    }

    /**
     * Returns the DOM structure which is transformed to a byte array and set to
     * the camel message body.
     * 
     * @param relevantReferences
     *            input from method
     *            {@link #getReferencesForMessageMapping(ReferencesAndObjects)}
     * @param relevantObjects
     *            input from method
     *            {@link #getObjectsForMessageMapping(ReferencesAndObjects)}
     * @return dom structure
     * @throws Exception
     *             if an error occurs
     */
    protected DOMStructure getDomStructureForMessageBody(List<Reference> relevantReferences, List<XMLObject> relevantObjects)
        throws Exception { //NOPMD

        List<XMLObject> referencedObjects = getReferencedSameDocumentObjects(relevantReferences, relevantObjects);

        if (referencedObjects.isEmpty()) {
            throw new XmlSignatureException(
                    String.format("Unsupported XML signature document: Content object not found in the enveloping XML signature."));
        }

        if (referencedObjects.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < referencedObjects.size(); i++) {
                XMLObject xmlOb = referencedObjects.get(i);
                sb.append(xmlOb.getId());
                if (i < referencedObjects.size() - 1) {
                    sb.append(", ");
                }
            }
            throw new XmlSignatureException(String.format(
                    "Unsupported XML signature document: More than one content objects found. Object IDs: %s", sb.toString()));
        }

        @SuppressWarnings("unchecked")
        List<XMLStructure> structures = referencedObjects.get(0).getContent();
        if (structures.size() == 0) {
            throw new XmlSignatureException(
                    "Unsupported XML signature: XML signature is not enveloping; content not found in XML signature: structure list is empty.");
        }
        if (structures.size() > 1) {
            throw new XmlSignatureException("Unsupported XML signature: more than one structure elements in referenced content object.");
        }
        XMLStructure structure = structures.get(0);
        // only dom currently supported
        DOMStructure domStruc = (DOMStructure) structure;
        return domStruc;
    }

    protected List<XMLObject> getReferencedSameDocumentObjects(List<Reference> relevantReferences, List<XMLObject> relevantObjects) {
        List<XMLObject> referencedObjects = new ArrayList<XMLObject>(1);

        for (Reference ref : relevantReferences) {
            String refUri = getSameDocumentReferenceUri(ref);
            if (refUri == null) {
                continue;
            }
            XMLObject referencedOb = getReferencedObject(relevantObjects, refUri);
            if (referencedOb != null) {
                referencedObjects.add(referencedOb);
                continue;
            }
            // content could also be indirectly referenced via manifest
            addManifestReferencedObjects(relevantObjects, referencedObjects, refUri);
        }
        return referencedObjects;
    }

    @SuppressWarnings("unchecked")
    protected void addManifestReferencedObjects(List<XMLObject> allObjects, List<XMLObject> referencedObjects, String manifestId) {
        Manifest manifest = getReferencedManifest(allObjects, manifestId);
        if (manifest == null) {
            return;
        }
        for (Reference manifestRef : (List<Reference>) manifest.getReferences()) {
            String manifestRefUri = getSameDocumentReferenceUri(manifestRef);
            if (manifestRefUri == null) {
                continue;
            }
            XMLObject manifestReferencedOb = getReferencedObject(allObjects, manifestRefUri);
            if (manifestReferencedOb != null) {
                referencedObjects.add(manifestReferencedOb);
            }
        }
    }

    protected String getSameDocumentReferenceUri(Reference ref) {
        String refUri = ref.getURI();
        if (refUri == null) {
            LOG.warn("Ignoring reference {} which has no URI", ref);
            return null;
        }
        if (!refUri.startsWith("#")) {
            LOG.warn("Ignoring non-same document reference {}", refUri);
            return null;
        }
        return refUri.substring(1);
    }

    protected Manifest getReferencedManifest(List<XMLObject> objects, String id) {
        for (XMLObject xo : objects) {
            @SuppressWarnings("unchecked")
            List<XMLStructure> content = xo.getContent();
            for (XMLStructure xs : content) {
                if (xs instanceof Manifest) {
                    Manifest man = (Manifest) xs;
                    if (id.equals(man.getId())) {
                        return man;
                    }
                }
            }
        }
        return null;
    }

    protected XMLObject getReferencedObject(List<XMLObject> objects, String id) {
        for (XMLObject ob : objects) {
            if (id.equals(ob.getId())) {
                return ob;
            }
        }
        return null;
    }
}
