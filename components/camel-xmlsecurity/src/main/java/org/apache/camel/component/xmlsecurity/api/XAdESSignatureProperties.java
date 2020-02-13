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
package org.apache.camel.component.xmlsecurity.api;

import java.io.IOException;
import java.io.StringReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.security.auth.x500.X500Principal;
import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLObject;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.apache.camel.Message;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * Implementation of the XAdES-BES and XAdES-EPES properties defined in
 * http://www.etsi.org/deliver/etsi_ts%5C101900_101999%5C101903%5C01.04
 * .02_60%5Cts_101903v010402p.pdf. XAdES-T and XAdES-C is not implemented.
 * <p>
 * You have to overwrite the method {@link #getSigningCertificate()} or
 * {@link #getSigningCertificateChain()} if you want to have a
 * 'SigningCertificate' element in your XML Signature.
 * <p>
 * Further limitations:
 * <ul>
 * <li>No support for the 'QualifyingPropertiesReference' element (see section
 * 6.3.2 of spec).</li>
 * <li>No support for the 'Transforms' element contained in the
 * 'SignaturePolicyId' element contained in 'SignaturePolicyIdentifier' element</li>
 * <li>No support of the 'CounterSignature' element --> no support for the
 * 'UnsignedProperties' element</li>
 * <li>A 'CommitmentTypeIndication' element contains always the
 * 'AllSignedDataObjects' element. The 'ObjectReference' element within the
 * 'CommitmentTypeIndication' element is not supported.</li>
 * <li>The 'AllDataObjectsTimeStamp' element is not supported (it requires a
 * time authority)</li>
 * <li>The 'IndividualDataObjectsTimeStamp' element is not supported (it
 * requires a time authority)</li>
 * </ul>
 */
public class XAdESSignatureProperties implements XmlSignatureProperties {

    public static final String HTTP_URI_ETSI_ORG_01903_V1_3_2 = "http://uri.etsi.org/01903/v1.3.2#";

    public static final String HTTP_URI_ETSI_ORG_01903_V1_1_1 = "http://uri.etsi.org/01903/v1.1.1#";

    public static final String HTTP_URI_ETSI_ORG_01903_V1_2_2 = "http://uri.etsi.org/01903/v1.2.2#";

    public static final String SIG_POLICY_NONE = "None";

    public static final String SIG_POLICY_IMPLIED = "Implied";

    public static final String SIG_POLICY_EXPLICIT_ID = "ExplicitId";

    private static final Logger LOG = LoggerFactory.getLogger(XAdESSignatureProperties.class);

    private static final Set<String> SIG_POLICY_VALUES = new TreeSet<>();

    private boolean addSigningTime = true;

    private String namespace = HTTP_URI_ETSI_ORG_01903_V1_3_2;

    private String prefix = "etsi";

    private List<String> signingCertificateURIs = Collections.emptyList();

    private String digestAlgorithmForSigningCertificate = DigestMethod.SHA256; //"http://www.w3.org/2000/09/xmldsig#sha1";

    private String signaturePolicy = SIG_POLICY_NONE;

    private String sigPolicyId;

    private String sigPolicyIdQualifier;

    private String sigPolicyIdDescription;

    private List<String> sigPolicyIdDocumentationReferences = Collections.emptyList();

    private String signaturePolicyDigestAlgorithm = DigestMethod.SHA256; //"http://www.w3.org/2000/09/xmldsig#sha1";

    private String signaturePolicyDigestValue;

    private List<String> sigPolicyQualifiers = Collections.emptyList();

    private String dataObjectFormatDescription;

    private String dataObjectFormatMimeType;

    private String dataObjectFormatIdentifier;

    private String dataObjectFormatIdentifierQualifier;

    private String dataObjectFormatIdentifierDescription;

    private List<String> dataObjectFormatIdentifierDocumentationReferences = Collections.emptyList();

    private List<String> signerClaimedRoles = Collections.emptyList();

    private List<XAdESEncapsulatedPKIData> signerCertifiedRoles = Collections.emptyList();

    private String signatureProductionPlaceCity;

    private String signatureProductionPlaceStateOrProvince;

    private String signatureProductionPlacePostalCode;

    private String signatureProductionPlaceCountryName;

    private String commitmentTypeId;

    private String commitmentTypeIdQualifier;

    private String commitmentTypeIdDescription;

    private List<String> commitmentTypeIdDocumentationReferences = Collections.emptyList();

    private List<String> commitmentTypeQualifiers = Collections.emptyList();

    static {
        SIG_POLICY_VALUES.add(SIG_POLICY_NONE);
        SIG_POLICY_VALUES.add(SIG_POLICY_IMPLIED);
        SIG_POLICY_VALUES.add(SIG_POLICY_EXPLICIT_ID);
    }

    public XAdESSignatureProperties() {
    }

    public boolean isAddSigningTime() {
        return addSigningTime;
    }

    public void setAddSigningTime(boolean addSigningTime) {
        this.addSigningTime = addSigningTime;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        if (namespace == null) {
            throw new IllegalArgumentException("Parameter 'namespace' is null");
        }
        this.namespace = namespace;
    }

    protected String findNamespace(Message message) {
        return message.getHeader(XmlSignatureConstants.HEADER_XADES_NAMESPACE, getNamespace(), String.class);
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    protected String findPrefix(Message message) {
        return message.getHeader(XmlSignatureConstants.HEADER_XADES_PREFIX, getPrefix(), String.class);
    }

    /**
     * URIs of the signing certificate or signing certificate chain. For the
     * sining certificate the first URI is taken. If there is a signing
     * certificate chain specified, then the URIs are assigned to the
     * certificates in the chain in the order given in the provided list. You
     * have to specify an empty entry (null or empty srting), if no URI should
     * be assigned to a specific certificate in the list. If you specify an
     * empty list, then no URIs are assigned.
     * 
     * @throws IllegalArgumentException
     *             if the parameter is <code>null</code> or one of the URIs is
     *             <code>null</code>
     */
    public void setSigningCertificateURIs(List<String> signingCertificateURIs) {
        if (signingCertificateURIs == null) {
            throw new IllegalArgumentException("Parameter 'signingCertificateURIs' is null");
        }
        this.signingCertificateURIs = new ArrayList<>(signingCertificateURIs);
    }

    public List<String> getSigningCertificateURIs() {
        return signingCertificateURIs;
    }

    public String getDigestAlgorithmForSigningCertificate() {
        return digestAlgorithmForSigningCertificate;
    }

    /**
     * Digest Algorithm for creating the digest of the signing certificate.
     * Possible values: "http://www.w3.org/2000/09/xmldsig#sha1",
     * "http://www.w3.org/2001/04/xmlenc#sha256",
     * "http://www.w3.org/2001/04/xmldsig-more#sha384",
     * "http://www.w3.org/2001/04/xmlenc#sha512". Default value is
     * "http://www.w3.org/2001/04/xmlenc#sha256".
     * 
     */
    public void setDigestAlgorithmForSigningCertificate(String digestAlgorithm) {
        this.digestAlgorithmForSigningCertificate = digestAlgorithm;
    }

    public String getSignaturePolicy() {
        return signaturePolicy;
    }

    /**
     * Signature Policy. Possible values: {@link #SIG_POLICY_NONE},
     * {@link #SIG_POLICY_IMPLIED}, {@link #SIG_POLICY_EXPLICIT_ID}. Default
     * value is {@link #SIG_POLICY_NONE}.
     * 
     */
    public void setSignaturePolicy(String signaturePolicy) {
        if (!SIG_POLICY_VALUES.contains(signaturePolicy)) {
            throw new IllegalArgumentException(String.format(
                    "Signature policy '%s' is invalid. Possible values are 'None', 'Implied', and 'ExplicitId'.", signaturePolicy));
        }
        this.signaturePolicy = signaturePolicy;
    }

    public String getSigPolicyId() {
        return sigPolicyId;
    }

    /**
     * Identifier must be specified if {@link #getSignaturePolicy()} equals
     * "ExplicitId". Must be an URI
     */
    public void setSigPolicyId(String sigPolicyId) {
        this.sigPolicyId = sigPolicyId;
    }

    public String getSigPolicyIdQualifier() {
        return sigPolicyIdQualifier;
    }

    /**
     * Qualifier for the Signature Policy Identifier. Possible values are
     * <code>null</code> (which means no Qualifier element is created),
     * "OIDAsURI", or "OIDAsURN". Default value is <code>null</code>. If the
     * identifier is an OID then a qualifier must be set.
     */
    public void setSigPolicyIdQualifier(String sigPolicyIdQualifier) {
        this.sigPolicyIdQualifier = sigPolicyIdQualifier;
    }

    public String getSigPolicyIdDescription() {
        return sigPolicyIdDescription;
    }

    public void setSigPolicyIdDescription(String sigPolicyIdDescription) {
        this.sigPolicyIdDescription = sigPolicyIdDescription;
    }

    public List<String> getSigPolicyIdDocumentationReferences() {
        return sigPolicyIdDocumentationReferences;
    }

    /**
     * 
     * Sets the documentation references of the signature policy.
     * 
     * @throws IllegalArgumentException
     *             if the parameter is <code>null</code> or one of the
     *             documentation references is <code>null</code> or empty
     */
    public void setSigPolicyIdDocumentationReferences(List<String> sigPolicyIdDocumentationReferences) {
        if (sigPolicyIdDocumentationReferences == null) {
            throw new IllegalArgumentException("Parameter 'sigPolicyIdDocumentationReferences' is null");
        }
        for (String ref : sigPolicyIdDocumentationReferences) {
            if (ref == null || ref.isEmpty()) {
                throw new IllegalArgumentException("At least one documentation reference of the signature policy is null or empty");
            }
        }
        this.sigPolicyIdDocumentationReferences = sigPolicyIdDocumentationReferences;
    }

    public String getSignaturePolicyDigestAlgorithm() {
        return signaturePolicyDigestAlgorithm;
    }

    /**
     * Digest Algorithm for creating the digest of the signature policy
     * document. Possible values: "http://www.w3.org/2000/09/xmldsig#sha1",
     * "http://www.w3.org/2001/04/xmlenc#sha256",
     * "http://www.w3.org/2001/04/xmldsig-more#sha384",
     * "http://www.w3.org/2001/04/xmlenc#sha512". Default value is
     * "http://www.w3.org/2001/04/xmlenc#sha256".
     * 
     */
    public void setSignaturePolicyDigestAlgorithm(String signaturePolicyDigestAlgorithm) {
        this.signaturePolicyDigestAlgorithm = signaturePolicyDigestAlgorithm;
    }

    public String getSignaturePolicyDigestValue() {
        return signaturePolicyDigestValue;
    }

    /** Digest value for the signature policy base 64 encoded. */
    public void setSignaturePolicyDigestValue(String signaturePolicyDigestValue) {
        this.signaturePolicyDigestValue = signaturePolicyDigestValue;
    }

    public List<String> getSigPolicyQualifiers() {
        return sigPolicyQualifiers;
    }

    /**
     * Sets the signature policy qualifiers. Each qualifier can be a text or a
     * XML fragment with the root element 'SigPolicyQualifier' with the XAdES
     * namespace.
     * 
     * @throws IllegalArgumentException
     *             if the input parameter is <code>null</code>, or one of the
     *             qualifiers is <code>null</code> or empty
     * 
     */
    public void setSigPolicyQualifiers(List<String> sigPolicyQualifiers) {
        if (sigPolicyQualifiers == null) {
            throw new IllegalArgumentException("Parameter 'sigPolicyQualifiers' is null");
        }
        for (String qualifier : sigPolicyQualifiers) {
            if (qualifier == null || qualifier.isEmpty()) {
                throw new IllegalArgumentException("At least one of the policy qualifiers is null or empty");
            }
        }
        this.sigPolicyQualifiers = new ArrayList<>(sigPolicyQualifiers);
    }

    public String getDataObjectFormatDescription() {
        return dataObjectFormatDescription;
    }

    public void setDataObjectFormatDescription(String dataObjectFormatDescription) {
        this.dataObjectFormatDescription = dataObjectFormatDescription;
    }

    public String getDataObjectFormatMimeType() {
        return dataObjectFormatMimeType;
    }

    public void setDataObjectFormatMimeType(String dataObjectFormatMimeType) {
        this.dataObjectFormatMimeType = dataObjectFormatMimeType;
    }

    public String getDataObjectFormatIdentifier() {
        return dataObjectFormatIdentifier;
    }

    public void setDataObjectFormatIdentifier(String dataObjectFormatIdentifier) {
        this.dataObjectFormatIdentifier = dataObjectFormatIdentifier;
    }

    public String getDataObjectFormatIdentifierQualifier() {
        return dataObjectFormatIdentifierQualifier;
    }

    /**
     * Qualifier for the Format Identifier. Possible values are
     * <code>null</code> (which means no Qualifier element is created),
     * "OIDAsURI", or "OIDAsURN". Default value is <code>null</code>. If the
     * identifier is an OID then a qualifier must be set.
     */
    public void setDataObjectFormatIdentifierQualifier(String dataObjectFormatIdentifierQualifier) {
        this.dataObjectFormatIdentifierQualifier = dataObjectFormatIdentifierQualifier;
    }

    public String getDataObjectFormatIdentifierDescription() {
        return dataObjectFormatIdentifierDescription;
    }

    public void setDataObjectFormatIdentifierDescription(String dataObjectFormatIdentifierDescription) {
        this.dataObjectFormatIdentifierDescription = dataObjectFormatIdentifierDescription;
    }

    public List<String> getDataObjectFormatIdentifierDocumentationReferences() {
        return dataObjectFormatIdentifierDocumentationReferences;
    }

    /**
     * 
     * Sets the documentation references of the data object format identifier.
     * 
     * @throws IllegalArgumentException
     *             if the parameter is <code>null</code> or one of the
     *             documentation references is <code>null</code> or empty
     */
    public void setDataObjectFormatIdentifierDocumentationReferences(List<String> dataObjectFormatIdentifierDocumentationReferences) {
        if (dataObjectFormatIdentifierDocumentationReferences == null) {
            throw new IllegalArgumentException("Parameter 'dataObjectFormatIdentifierDocumentationReferences' is null");
        }
        for (String ref : dataObjectFormatIdentifierDocumentationReferences) {
            if (ref == null || ref.isEmpty()) {
                throw new IllegalArgumentException("At least one reference of the identifier of the data object format is null or empty");
            }
        }
        this.dataObjectFormatIdentifierDocumentationReferences = new ArrayList<>(dataObjectFormatIdentifierDocumentationReferences);
    }

    public List<String> getSignerClaimedRoles() {
        return signerClaimedRoles;
    }

    /**
     * Sets the claimed roles list. A role can be either a text or a XML
     * fragment with the root element 'ClaimedRole' with the XAdES namespace.
     * 
     * @throws IllegalArgumentException
     *             if <tt>signerClaimedRoles</tt> is <code>null</code>, or if
     *             one of the roles is <code>null</code> or empty
     */
    public void setSignerClaimedRoles(List<String> signerClaimedRoles) {
        if (signerClaimedRoles == null) {
            throw new IllegalArgumentException("Parameter 'signerClaimedRoles' is null");
        }
        for (String role : signerClaimedRoles) {
            if (role == null || role.isEmpty()) {
                throw new IllegalArgumentException("At least one of the signer claimed roles is null or empty");
            }
        }
        this.signerClaimedRoles = new ArrayList<>(signerClaimedRoles);
    }

    public List<XAdESEncapsulatedPKIData> getSignerCertifiedRoles() {
        return signerCertifiedRoles;
    }

    /**
     * Sets the certified roles.
     * 
     * @throws IllegalArgumentException
     *             if <tt>signerCertifiedRoles</tt> is <code>null</code>
     */
    public void setSignerCertifiedRoles(List<XAdESEncapsulatedPKIData> signerCertifiedRoles) {
        if (signerCertifiedRoles == null) {
            throw new IllegalArgumentException("Parameter 'signerCertifiedRoles' is null");
        }
        for (XAdESEncapsulatedPKIData role : signerCertifiedRoles) {
            if (role == null) {
                throw new IllegalArgumentException("At least one of the signer certified roles is null");
            }
        }
        this.signerCertifiedRoles = new ArrayList<>(signerCertifiedRoles);
    }

    public String getSignatureProductionPlaceCity() {
        return signatureProductionPlaceCity;
    }

    public void setSignatureProductionPlaceCity(String signatureProductionPlaceCity) {
        this.signatureProductionPlaceCity = signatureProductionPlaceCity;
    }

    public String getSignatureProductionPlaceStateOrProvince() {
        return signatureProductionPlaceStateOrProvince;
    }

    public void setSignatureProductionPlaceStateOrProvince(String signatureProductionPlaceStateOrProvince) {
        this.signatureProductionPlaceStateOrProvince = signatureProductionPlaceStateOrProvince;
    }

    public String getSignatureProductionPlacePostalCode() {
        return signatureProductionPlacePostalCode;
    }

    public void setSignatureProductionPlacePostalCode(String signatureProductionPlacePostalCode) {
        this.signatureProductionPlacePostalCode = signatureProductionPlacePostalCode;
    }

    public String getSignatureProductionPlaceCountryName() {
        return signatureProductionPlaceCountryName;
    }

    public void setSignatureProductionPlaceCountryName(String signatureProductionPlaceCountryName) {
        this.signatureProductionPlaceCountryName = signatureProductionPlaceCountryName;
    }

    public String getCommitmentTypeId() {
        return commitmentTypeId;
    }

    public void setCommitmentTypeId(String commitmentTypeId) {
        this.commitmentTypeId = commitmentTypeId;
    }

    public String getCommitmentTypeIdQualifier() {
        return commitmentTypeIdQualifier;
    }

    /**
     * Qualifier for the Commitment Type ID. Possible values are
     * <code>null</code> (which means no Qualifier element is created),
     * "OIDAsURI", or "OIDAsURN". Default value is <code>null</code>. If the
     * identifier is an OID then a qualifier must be set.
     */
    public void setCommitmentTypeIdQualifier(String commitmentTypeIdQualifier) {
        this.commitmentTypeIdQualifier = commitmentTypeIdQualifier;
    }

    public String getCommitmentTypeIdDescription() {
        return commitmentTypeIdDescription;
    }

    public void setCommitmentTypeIdDescription(String commitmentTypeIdDescription) {
        this.commitmentTypeIdDescription = commitmentTypeIdDescription;
    }

    public List<String> getCommitmentTypeIdDocumentationReferences() {
        return commitmentTypeIdDocumentationReferences;
    }

    /**
     * Sets the documentation references for the Commitment Type ID:
     * 
     * @throws IllegalArgumentException
     *             if the parameter is <code>null</code> or a documentation
     *             reference is <code>null</code> or empty
     * 
     */
    public void setCommitmentTypeIdDocumentationReferences(List<String> commitmentTypeIdDocumentationReferences) {
        if (commitmentTypeIdDocumentationReferences == null) {
            throw new IllegalArgumentException("Parameter 'commitmentTypeIdDocumentationReferences' is null");
        }
        for (String ref : commitmentTypeIdDocumentationReferences) {
            if (ref == null || ref.isEmpty()) {
                throw new IllegalArgumentException("At least one documentation reference of the commitment type is null or empty");
            }
        }
        this.commitmentTypeIdDocumentationReferences = new ArrayList<>(commitmentTypeIdDocumentationReferences);
    }

    public List<String> getCommitmentTypeQualifiers() {
        return commitmentTypeQualifiers;
    }

    /**
     * List of additional qualifying information on the commitment. Each list
     * element can be a text or an XML fragment with the root element
     * 'CommitmentTypeQualifier' with the XAdES namespace.
     * 
     * @throws IllegalArgumentException
     *             if the input parameter is <code>null</code>, or one qualifier
     *             is <code>null</code> or empty
     */
    public void setCommitmentTypeQualifiers(List<String> commitmentTypeQualifiers) {
        if (commitmentTypeQualifiers == null) {
            throw new IllegalArgumentException("Parameter 'commitmentTypeQualifiers' is null");
        }
        for (String qualifier : commitmentTypeQualifiers) {
            if (qualifier == null || qualifier.isEmpty()) {
                throw new IllegalArgumentException("At least one qualifier of the commitment type is null or empty");
            }
        }
        this.commitmentTypeQualifiers = new ArrayList<>(commitmentTypeQualifiers);
    }

    @Override
    public Output get(Input input) throws Exception {

        XmlSignatureProperties.Output result = new Output();

        if (!isAddSignedSignatureProperties() && !isAddSignedDataObjectPropeties()) {
            LOG.debug("XAdES signature properties are empty. Therefore no XAdES element will be added to the signature.");
            return result;
        }
        String signedPropertiesId = "_" + UUID.randomUUID().toString();
        List<Transform> transforms = Collections.emptyList();
        Reference ref = input.getSignatureFactory().newReference("#" + signedPropertiesId,
                input.getSignatureFactory().newDigestMethod(input.getContentDigestAlgorithm(), null), transforms,
                "http://uri.etsi.org/01903#SignedProperties", null);

        Node parent = input.getParent();
        Document doc;
        if (Node.DOCUMENT_NODE == parent.getNodeType()) {
            doc = (Document) parent; // enveloping
        } else {
            doc = parent.getOwnerDocument(); // enveloped
        }

        Element qualifyingProperties = createElement("QualifyingProperties", doc, input);
        setIdAttributeFromHeader(XmlSignatureConstants.HEADER_XADES_QUALIFYING_PROPERTIES_ID, qualifyingProperties, input);
        String signatureId = input.getSignatureId();
        if (signatureId == null || signatureId.isEmpty()) {
            LOG.debug("No signature Id configured. Therefore a value is generated.");
            // generate one
            signatureId = "_" + UUID.randomUUID().toString();
            // and set to output
            result.setSignatureId(signatureId);
        }
        setAttribute(qualifyingProperties, "Target", "#" + signatureId);
        Element signedProperties = createElement("SignedProperties", doc, input);
        qualifyingProperties.appendChild(signedProperties);
        setAttribute(signedProperties, "Id", signedPropertiesId);
        signedProperties.setIdAttribute("Id", true);
        addSignedSignatureProperties(doc, signedProperties, input);
        String contentReferenceId = addSignedDataObjectProperties(doc, signedProperties, input);
        result.setContentReferenceId(contentReferenceId);
        DOMStructure structure = new DOMStructure(qualifyingProperties);

        XMLObject propertiesObject = input.getSignatureFactory().newXMLObject(Collections.singletonList(structure), null, null, null);

        result.setReferences(Collections.singletonList(ref));
        result.setObjects(Collections.singletonList(propertiesObject));

        return result;
    }

    protected void setAttribute(Element element, String attrName, String value) {
        //  element.setAttribute(name, value); did cause NullPointerException in santuario 2.02
        element.setAttributeNS("", attrName, value);
    }

    protected void setIdAttributeFromHeader(String header, Element element, Input input) {
        String value = input.getMessage().getHeader(header, String.class);
        if (value != null && !value.isEmpty()) {
            setAttribute(element, "Id", value);
            element.setIdAttribute("Id", true);
        }
    }

    protected String addSignedDataObjectProperties(Document doc, Element signedProperties, Input input) throws XmlSignatureException,
            SAXException, IOException, ParserConfigurationException {
        if (isAddSignedDataObjectPropeties()) {
            Element signedDataObjectProperties = createElement("SignedDataObjectProperties", doc, input);
            setIdAttributeFromHeader(XmlSignatureConstants.HEADER_XADES_SIGNED_DATA_OBJECT_PROPERTIES_ID, signedDataObjectProperties, input);
            signedProperties.appendChild(signedDataObjectProperties);
            String contentReferenceId = addDataObjectFormat(signedDataObjectProperties, doc, input);
            addCommitmentTypeIndication(signedDataObjectProperties, doc, input);
            return contentReferenceId;
        } else {
            return null;
        }
    }

    protected boolean isAddSignedDataObjectPropeties() {
        return isAddDataObjectFormat() || isAddCommitmentType();
    }

    protected void addCommitmentTypeIndication(Element signedDataObjectProperties, Document doc, Input input) throws SAXException,
            IOException, ParserConfigurationException, XmlSignatureException {
        if (!isAddCommitmentType()) {
            return;
        }
        Element commitmentTypeIndication = createElement("CommitmentTypeIndication", doc, input);
        signedDataObjectProperties.appendChild(commitmentTypeIndication);
        Element commitmentTypeIdEl = createElement("CommitmentTypeId", doc, input);
        commitmentTypeIndication.appendChild(commitmentTypeIdEl);
        Element identifier = createElement("Identifier", doc, input);
        commitmentTypeIdEl.appendChild(identifier);
        identifier.setTextContent(getCommitmentTypeId());
        if (getCommitmentTypeIdQualifier() != null && !getCommitmentTypeIdQualifier().isEmpty()) {
            setAttribute(identifier, "Qualifier", getCommitmentTypeIdQualifier());
        }
        if (getCommitmentTypeIdDescription() != null && !getCommitmentTypeIdDescription().isEmpty()) {
            Element description = createElement("Description", doc, input);
            commitmentTypeIdEl.appendChild(description);
            description.setTextContent(getCommitmentTypeIdDescription());
        }
        if (!getCommitmentTypeIdDocumentationReferences().isEmpty()) {
            Element documentationReferences = createElement("DocumentationReferences", doc, input);
            commitmentTypeIdEl.appendChild(documentationReferences);
            List<String> docReferences = getCommitmentTypeIdDocumentationReferences();
            for (String documentationReferenceValue : docReferences) {
                Element documentationReference = createElement("DocumentationReference", doc, input);
                documentationReferences.appendChild(documentationReference);
                documentationReference.setTextContent(documentationReferenceValue);
            }
        }
        Element allSignedDataObjects = createElement("AllSignedDataObjects", doc, input);
        commitmentTypeIndication.appendChild(allSignedDataObjects);

        List<String> qualifiers = getCommitmentTypeQualifiers();
        if (!qualifiers.isEmpty()) {
            Element qualifiersEl = createElement("CommitmentTypeQualifiers", doc, input);
            commitmentTypeIndication.appendChild(qualifiersEl);
            String errorMessage = "The XAdES configuration is invalid. The list of the commitment type qualifiers contains the invalid entry '%s'. An entry must either be a text or an XML fragment "
                    + "with the root element '%s' with the namespace '%s'.";
            for (String qualifier : getCommitmentTypeQualifiers()) {
                Element qualifierEl = createChildFromXmlFragmentOrText(doc, input, "CommitmentTypeQualifier", errorMessage, qualifier);
                qualifiersEl.appendChild(qualifierEl);
            }
        }
    }

    protected boolean isAddCommitmentType() {
        return getCommitmentTypeId() != null && !getCommitmentTypeId().isEmpty();
    }

    protected String addDataObjectFormat(Element signedDataObjectProperties, Document doc, Input input) throws XmlSignatureException {
        if (!isAddDataObjectFormat()) {
            return null;
        }
        Element dataObjectFormat = createElement("DataObjectFormat", doc, input);
        signedDataObjectProperties.appendChild(dataObjectFormat);
        String contentReferenceId = "_" + UUID.randomUUID().toString();
        setAttribute(dataObjectFormat, "ObjectReference", "#" + contentReferenceId);

        if (getDataObjectFormatDescription() != null && !getDataObjectFormatDescription().isEmpty()) {
            Element description = createElement("Description", doc, input);
            dataObjectFormat.appendChild(description);
            description.setTextContent(getDataObjectFormatDescription());
        }
        if (getDataObjectFormatIdentifier() != null && !getDataObjectFormatIdentifier().isEmpty()) {
            Element objectIdentifier = createElement("ObjectIdentifier", doc, input);
            dataObjectFormat.appendChild(objectIdentifier);
            Element identifier = createElement("Identifier", doc, input);
            objectIdentifier.appendChild(identifier);

            identifier.setTextContent(getDataObjectFormatIdentifier());
            if (getDataObjectFormatIdentifierQualifier() != null && !getDataObjectFormatIdentifierQualifier().isEmpty()) {
                setAttribute(identifier, "Qualifier", getDataObjectFormatIdentifierQualifier());
            }
            if (getDataObjectFormatIdentifierDescription() != null && !getDataObjectFormatIdentifierDescription().isEmpty()) {
                Element description = createElement("Description", doc, input);
                objectIdentifier.appendChild(description);
                description.setTextContent(getDataObjectFormatIdentifierDescription());
            }
            if (!getDataObjectFormatIdentifierDocumentationReferences().isEmpty()) {
                Element documentationReferences = createElement("DocumentationReferences", doc, input);
                objectIdentifier.appendChild(documentationReferences);
                List<String> docReferences = getDataObjectFormatIdentifierDocumentationReferences();
                for (String documentationReferenceValue : docReferences) {
                    Element documentationReference = createElement("DocumentationReference", doc, input);
                    documentationReferences.appendChild(documentationReference);
                    documentationReference.setTextContent(documentationReferenceValue);
                }
            }

        }
        if (getDataObjectFormatMimeType() != null && !getDataObjectFormatMimeType().isEmpty()) {
            Element mimeType = createElement("MimeType", doc, input);
            dataObjectFormat.appendChild(mimeType);
            mimeType.setTextContent(getDataObjectFormatMimeType());
        }
        String encoding = input.getMessage().getHeader(XmlSignatureConstants.HEADER_XADES_DATA_OBJECT_FORMAT_ENCODING, String.class);
        if (encoding != null && !encoding.isEmpty()) {
            Element encodingEl = createElement("Encoding", doc, input);
            dataObjectFormat.appendChild(encodingEl);
            encodingEl.setTextContent(encoding);
        }
        return contentReferenceId;
    }

    protected boolean isAddDataObjectFormat() {
        return (getDataObjectFormatIdentifier() != null && !getDataObjectFormatIdentifier().isEmpty())
                || (getDataObjectFormatDescription() != null && !getDataObjectFormatDescription().isEmpty())
                || (getDataObjectFormatMimeType() != null && !getDataObjectFormatMimeType().isEmpty());
    }

    protected void addSignedSignatureProperties(Document doc, Element signedProperties, Input input) throws Exception {
        if (isAddSignedSignatureProperties()) {
            LOG.debug("Adding signed signature properties");
            Element signedSignatureProperties = createElement("SignedSignatureProperties", doc, input);
            setIdAttributeFromHeader(XmlSignatureConstants.HEADER_XADES_SIGNED_SIGNATURE_PROPERTIES_ID, signedSignatureProperties, input);
            signedProperties.appendChild(signedSignatureProperties);
            addSigningTime(doc, signedSignatureProperties, input);
            addSigningCertificate(doc, signedSignatureProperties, input);
            addSignaturePolicyIdentifier(doc, signedSignatureProperties, input);
            addSignatureProductionPlace(doc, signedSignatureProperties, input);
            addSignerRole(doc, signedSignatureProperties, input);
        }
    }

    protected boolean isAddSignedSignatureProperties() throws Exception {
        return isAddSigningTime() || getSigningCertificate() != null
                || (getSigningCertificateChain() != null && getSigningCertificateChain().length > 0) || isAddSignaturePolicy()
                || isAddSignatureProductionPlace() || isAddSignerRole();
    }

    protected boolean isAddSignerRole() {
        return getSignerClaimedRoles().size() > 0 || getSignerCertifiedRoles().size() > 0;
    }

    protected void addSignatureProductionPlace(Document doc, Element signedSignatureProperties, Input input) {
        if (!isAddSignatureProductionPlace()) {
            return;
        }
        Element signatureProductionPlace = createElement("SignatureProductionPlace", doc, input);
        signedSignatureProperties.appendChild(signatureProductionPlace);
        if (getSignatureProductionPlaceCity() != null && !getSignatureProductionPlaceCity().isEmpty()) {
            LOG.debug("Adding production city");
            Element city = createElement("City", doc, input);
            signatureProductionPlace.appendChild(city);
            city.setTextContent(getSignatureProductionPlaceCity());
        }
        if (getSignatureProductionPlaceStateOrProvince() != null && !getSignatureProductionPlaceStateOrProvince().isEmpty()) {
            LOG.debug("Adding production state or province");
            Element stateOrProvince = createElement("StateOrProvince", doc, input);
            signatureProductionPlace.appendChild(stateOrProvince);
            stateOrProvince.setTextContent(getSignatureProductionPlaceStateOrProvince());
        }
        if (getSignatureProductionPlacePostalCode() != null && !getSignatureProductionPlacePostalCode().isEmpty()) {
            LOG.debug("Adding production postal code");
            Element postalCode = createElement("PostalCode", doc, input);
            signatureProductionPlace.appendChild(postalCode);
            postalCode.setTextContent(getSignatureProductionPlacePostalCode());
        }
        if (getSignatureProductionPlaceCountryName() != null && !getSignatureProductionPlaceCountryName().isEmpty()) {
            LOG.debug("Adding production country name");
            Element countryName = createElement("CountryName", doc, input);
            signatureProductionPlace.appendChild(countryName);
            countryName.setTextContent(getSignatureProductionPlaceCountryName());
        }
    }

    protected boolean isAddSignatureProductionPlace() {
        return isNotEmpty(getSignatureProductionPlaceCity()) || isNotEmpty(getSignatureProductionPlaceCountryName())
                || isNotEmpty(getSignatureProductionPlacePostalCode()) || isNotEmpty(getSignatureProductionPlaceStateOrProvince());
    }

    protected void addSignerRole(Document doc, Element signedSignatureProperties, Input input) throws XmlSignatureException, SAXException,
            IOException, ParserConfigurationException {
        if (!isAddSignerRole()) {
            return;
        }
        Element signerRole = createElement("SignerRole", doc, input);
        signedSignatureProperties.appendChild(signerRole);
        List<String> claimedRoles = getSignerClaimedRoles();
        if (!claimedRoles.isEmpty()) {
            LOG.debug("Adding claimed roles");
            Element claimedRolesEl = createElement("ClaimedRoles", doc, input);
            signerRole.appendChild(claimedRolesEl);
            String errorMessage = "The XAdES configuration is invalid. The list of the claimed roles contains the invalid entry '%s'."
                    + " An entry must either be a text or an XML fragment with the root element '%s' with the namespace '%s'.";
            for (String claimedRole : claimedRoles) {
                Element claimedRoleEl = createChildFromXmlFragmentOrText(doc, input, "ClaimedRole", errorMessage, claimedRole);
                claimedRolesEl.appendChild(claimedRoleEl);
            }
        }
        List<XAdESEncapsulatedPKIData> certifiedRoles = getSignerCertifiedRoles();
        if (!certifiedRoles.isEmpty()) {
            LOG.debug("Adding certified roles");
            Element certifiedRolesEl = createElement("CertifiedRoles", doc, input);
            signerRole.appendChild(certifiedRolesEl);
            for (XAdESEncapsulatedPKIData certifiedRole : certifiedRoles) {
                Element certifiedRoleEl = createElement("CertifiedRole", doc, input);
                certifiedRolesEl.appendChild(certifiedRoleEl);
                certifiedRoleEl.setTextContent(certifiedRole.getBase64Conent());
                if (certifiedRole.getEncoding() != null && !certifiedRole.getEncoding().isEmpty()) {
                    setAttribute(certifiedRoleEl, "Encoding", certifiedRole.getEncoding());
                }
                if (certifiedRole.getId() != null && !certifiedRole.getId().isEmpty()) {
                    setAttribute(certifiedRoleEl, "Id", certifiedRole.getId());
                    certifiedRoleEl.setIdAttribute("Id", true);
                }
            }
        }

    }

    protected void addSignaturePolicyIdentifier(Document doc, Element signedProperties, Input input) throws XmlSignatureException,
            SAXException, IOException, ParserConfigurationException {
        if (!isAddSignaturePolicy()) {
            return;
        }
        Element signaturePolicyIdentifier = createElement("SignaturePolicyIdentifier", doc, input);
        signedProperties.appendChild(signaturePolicyIdentifier);
        if (SIG_POLICY_IMPLIED.equals(getSignaturePolicy())) {
            LOG.debug("Adding implied signature policy");
            Element implied = createElement("SignaturePolicyImplied", doc, input);
            signaturePolicyIdentifier.appendChild(implied);
        } else if (SIG_POLICY_EXPLICIT_ID.equals(getSignaturePolicy())) {
            LOG.debug("Adding signatue policy ID");
            Element id = createElement("SignaturePolicyId", doc, input);
            signaturePolicyIdentifier.appendChild(id);
            Element sigPolicyId = createElement("SigPolicyId", doc, input);
            id.appendChild(sigPolicyId);
            Element identifier = createElement("Identifier", doc, input);
            sigPolicyId.appendChild(identifier);
            if (getSigPolicyId() == null || getSigPolicyId().isEmpty()) {
                throw new XmlSignatureException("The XAdES-EPES configuration is invalid. The signature policy identifier is missing.");
            }
            identifier.setTextContent(getSigPolicyId());
            if (getSigPolicyIdQualifier() != null && !getSigPolicyIdQualifier().isEmpty()) {
                setAttribute(identifier, "Qualifier", getSigPolicyIdQualifier());
            }
            if (getSigPolicyIdDescription() != null && !getSigPolicyIdDescription().isEmpty()) {
                Element description = createElement("Description", doc, input);
                sigPolicyId.appendChild(description);
                description.setTextContent(getSigPolicyIdDescription());
            }
            if (!getSigPolicyIdDocumentationReferences().isEmpty()) {
                Element documentationReferences = createElement("DocumentationReferences", doc, input);
                sigPolicyId.appendChild(documentationReferences);
                List<String> docReferences = getSigPolicyIdDocumentationReferences();
                for (String documentationReferenceValue : docReferences) {
                    Element documentationReference = createElement("DocumentationReference", doc, input);
                    documentationReferences.appendChild(documentationReference);
                    documentationReference.setTextContent(documentationReferenceValue);
                }
            }
            //here we could introduce the transformations for the signature policy, which we do not yet support
            Element sigPolicyHash = createElement("SigPolicyHash", doc, input);
            id.appendChild(sigPolicyHash);
            if (getSignaturePolicyDigestAlgorithm() == null || getSignaturePolicyDigestAlgorithm().isEmpty()) {
                throw new XmlSignatureException(
                        "The XAdES-EPES configuration is invalid. The digest algorithm for the signature policy is missing.");
            }
            Element digestMethod = createElementNS(doc, input, "DigestMethod");
            sigPolicyHash.appendChild(digestMethod);
            setAttribute(digestMethod, "Algorithm", getSignaturePolicyDigestAlgorithm());
            if (getSignaturePolicyDigestValue() == null || getSignaturePolicyDigestValue().isEmpty()) {
                throw new XmlSignatureException(
                        "The XAdES-EPES configuration is invalid. The digest value for the signature policy is missing.");
            }
            Element digestValue = createElementNS(doc, input, "DigestValue");
            sigPolicyHash.appendChild(digestValue);
            digestValue.setTextContent(getSignaturePolicyDigestValue());

            List<String> qualifiers = getSigPolicyQualifiers();
            if (!qualifiers.isEmpty()) {
                Element qualifiersEl = createElement("SigPolicyQualifiers", doc, input);
                id.appendChild(qualifiersEl);
                String errorMessage = "The XAdES configuration is invalid. The list of the signatue policy qualifiers contains the invalid entry '%s'."
                        + " An entry must either be a text or an XML fragment with the root element '%s' with the namespace '%s'.";
                for (String elementOrText : getSigPolicyQualifiers()) {
                    Element child = createChildFromXmlFragmentOrText(doc, input, "SigPolicyQualifier", errorMessage, elementOrText);
                    qualifiersEl.appendChild(child);
                }
            }
        } else {
            // cannot happen
            throw new IllegalStateException(String.format(
                    "Invalid value '%s' for parameter 'SignaturePolicy'. Possible values are: 'None', 'Implied', and 'ExplictId'.",
                    getSignaturePolicy()));
        }

    }

    protected Element createChildFromXmlFragmentOrText(Document doc, Input input, String localElementName, String errorMessage,
            String elementOrText) throws IOException, ParserConfigurationException, XmlSignatureException {
        String ending = localElementName + ">";
        Element child;
        if (elementOrText.startsWith("<") && elementOrText.endsWith(ending)) {
            try {
                // assume xml
                InputSource source = new InputSource(new StringReader(elementOrText));
                source.setEncoding("UTF-8");
                Document parsedDoc = XmlSignatureHelper.newDocumentBuilder(Boolean.TRUE).parse(source);
                replacePrefixes(parsedDoc, input);
                child = (Element) doc.adoptNode(parsedDoc.getDocumentElement());
                // check for correct namespace
                String ns = findNamespace(input.getMessage());
                if (!ns.equals(child.getNamespaceURI())) {
                    throw new XmlSignatureException(
                            String.format(
                                    "The XAdES configuration is invalid. The root element '%s' of the provided XML fragment '%s' has the invalid namespace '%s'. The correct namespace is '%s'.",
                                    child.getLocalName(), elementOrText, child.getNamespaceURI(), ns));
                }
            } catch (SAXException e) {
                throw new XmlSignatureException(String.format(errorMessage, elementOrText, localElementName, namespace), e);
            }
        } else {
            child = createElement(localElementName, doc, input);
            child.setTextContent(elementOrText);
        }
        return child;
    }

    protected void replacePrefixes(Document qualifierDoc, Input input) {

        Element el = qualifierDoc.getDocumentElement();
        replacePrefix(el, input);

        List<Element> childElements = getChildElements(el);

        List<Element> collectedNewChildElements = new ArrayList<>();
        for (; !childElements.isEmpty();) {
            collectedNewChildElements.clear();
            for (Element child : childElements) {
                replacePrefix(child, input);
                List<Element> newChildElements = getChildElements(child);
                collectedNewChildElements.addAll(newChildElements);
            }
            childElements = new ArrayList<>(collectedNewChildElements);
        }
    }

    protected List<Element> getChildElements(Element el) {
        List<Element> childElements = new ArrayList<>(5);
        NodeList children = el.getChildNodes();
        int length = children.getLength();
        for (int i = 0; i < length; i++) {
            Node child = children.item(i);
            if (Node.ELEMENT_NODE == child.getNodeType()) {
                childElements.add((Element) child);
            }
        }
        return childElements;
    }

    protected void replacePrefix(Element el, Input input) {
        replacePrefixForNode(el, input);
        NamedNodeMap nnm = el.getAttributes();
        List<Attr> xmlnsToBeRemoved = new ArrayList<>(2);
        int length = nnm.getLength();
        for (int i = 0; i < length; i++) {
            Node attr = nnm.item(i);
            replacePrefixForNode(attr, input);
            if (attr.getNodeType() == Node.ATTRIBUTE_NODE) {
                if ("xmlns".equals(attr.getLocalName()) || "xmlns".equals(attr.getPrefix())) {
                    if (XMLSignature.XMLNS.equals(attr.getTextContent()) || findNamespace(input.getMessage()).equals(attr.getTextContent())) {
                        xmlnsToBeRemoved.add((Attr) attr);
                    }
                }
            }
        }
        // remove xml namespace declaration for XML signature and XAdES namespace
        for (Attr toBeRemoved : xmlnsToBeRemoved) {
            el.removeAttributeNode(toBeRemoved);
        }

    }

    protected void replacePrefixForNode(Node node, Input input) {
        if (XMLSignature.XMLNS.equals(node.getNamespaceURI())) {
            node.setPrefix(input.getPrefixForXmlSignatureNamespace());
        } else if (findNamespace(input.getMessage()).equals(node.getNamespaceURI())) {
            node.setPrefix(findPrefix(input.getMessage()));
        }
    }

    protected boolean isAddSignaturePolicy() {
        return !SIG_POLICY_NONE.equals(getSignaturePolicy());
    }

    protected void addSigningCertificate(Document doc, Element signedProperties, Input input) throws Exception {
        if (getSigningCertificate() == null && (getSigningCertificateChain() == null || getSigningCertificateChain().length == 0)) {
            return;
        }
        // signed certificate
        Element signedCertificate = createElement("SigningCertificate", doc, input);
        signedProperties.appendChild(signedCertificate);
        if (getSigningCertificate() != null) {
            LOG.debug("Adding signing certificate");
            X509Certificate cert = getSigningCertificate();
            addCertificate(cert, signedCertificate, doc, 0, input);
        } else if (getSigningCertificateChain() != null && getSigningCertificateChain().length > 0) {
            Certificate[] certs = getSigningCertificateChain();
            int index = 0;
            for (Certificate cert : certs) {
                LOG.debug("Adding chain certtificate {}", index);
                X509Certificate x509Cert = (X509Certificate) cert;
                addCertificate(x509Cert, signedCertificate, doc, index, input);
                index++;
            }
        } else {
            // cannot happen
            throw new IllegalStateException("Unexpected exception");
        }
    }

    /**
     * Returns the signing certificate. If you want to have a
     * "SigningCertificate" element then either this method or the method
     * {@link #getSigningCertificateChain()} must return a value which is
     * different from <code>null</code> or an empty array.
     * <p>
     * This implementation returns <code>null</code>
     */
    protected X509Certificate getSigningCertificate() throws Exception {
        return null;
    }

    /**
     * Returns the signing certificate. If you want to have a
     * "SigningCertificate" element then either this method or the method
     * {@link #getSigningCertificate()} must return a value.
     * <p>
     * This implementation returns <code>null</code>
     */
    protected X509Certificate[] getSigningCertificateChain() throws Exception {
        return null;
    }

    protected void addSigningTime(Document doc, Element signedProperties, Input input) {
        if (isAddSigningTime()) {
            LOG.debug("Adding signing time");
            //signing time
            Element signingTime = createElement("SigningTime", doc, input);
            signedProperties.appendChild(signingTime);
            Date current = new Date();
            signingTime.setTextContent(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(current));
        }
    }

    protected void addCertificate(X509Certificate cert, Element signedCertificate, Document doc, int index, Input input)
        throws CertificateEncodingException, NoSuchAlgorithmException, XmlSignatureException {
        Element elCert = createElement("Cert", doc, input);
        signedCertificate.appendChild(elCert);

        String algorithm = getMessageDigestAlgorithm(getDigestAlgorithmForSigningCertificate(),
                "The digest algorithm '%s' for the signing certificate is invalid");
        String digest = calculateDigest(algorithm, cert.getEncoded());
        Element certDigest = createElement("CertDigest", doc, input);
        elCert.appendChild(certDigest);
        Element digestMethod = createElementNS(doc, input, "DigestMethod");
        certDigest.appendChild(digestMethod);
        setAttribute(digestMethod, "Algorithm", getDigestAlgorithmForSigningCertificate());
        Element digestValue = createElementNS(doc, input, "DigestValue");
        certDigest.appendChild(digestValue);
        digestValue.setTextContent(digest);

        Element issuerSerial = createElement("IssuerSerial", doc, input);
        elCert.appendChild(issuerSerial);
        Element x509IssuerName = createDigSigElement("X509IssuerName", doc, input.getPrefixForXmlSignatureNamespace());
        issuerSerial.appendChild(x509IssuerName);
        x509IssuerName.setTextContent(cert.getIssuerX500Principal().getName(X500Principal.RFC2253));
        Element x509SerialNumber = createDigSigElement("X509SerialNumber", doc, input.getPrefixForXmlSignatureNamespace());
        issuerSerial.appendChild(x509SerialNumber);
        x509SerialNumber.setTextContent(cert.getSerialNumber().toString());

        List<String> uris = getSigningCertificateURIs();
        if (!uris.isEmpty() && uris.size() > index) {
            String uri = uris.get(index);
            if (uri != null && !uri.isEmpty()) {
                setAttribute(elCert, "URI", uri);
            }
        }
    }

    protected String getMessageDigestAlgorithm(String xmlSigDigestMethod, String errorMessage) throws XmlSignatureException {
        String algorithm;
        if (DigestMethod.SHA1.equals(xmlSigDigestMethod)) {
            algorithm = "SHA-1";
        } else if (DigestMethod.SHA256.equals(xmlSigDigestMethod)) {
            algorithm = "SHA-256";
        } else if ("http://www.w3.org/2001/04/xmldsig-more#sha384".equals(xmlSigDigestMethod)) {
            algorithm = "SHA-384";
        } else if (DigestMethod.SHA512.equals(getDigestAlgorithmForSigningCertificate())) {
            algorithm = "SHA-512";
        } else {
            throw new XmlSignatureException(String.format(errorMessage, xmlSigDigestMethod));
        }
        return algorithm;
    }

    protected String calculateDigest(String algorithm, byte[] bytes) throws NoSuchAlgorithmException, CertificateEncodingException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] digestBytes = digest.digest(bytes);
        return new Base64().encodeAsString(digestBytes);
    }

    protected Element createElementNS(Document doc, Input input, String elementName) {
        Element digestMethod;
        if (HTTP_URI_ETSI_ORG_01903_V1_1_1.equals(findNamespace(input.getMessage()))) {
            digestMethod = createElement(elementName, doc, input);
        } else {
            digestMethod = createDigSigElement(elementName, doc, input.getPrefixForXmlSignatureNamespace());
        }
        return digestMethod;
    }

    protected Element createDigSigElement(String localName, Document doc, String prefixForXmlSignatureNamespace) {
        Element el = doc.createElementNS("http://www.w3.org/2000/09/xmldsig#", localName);
        if (prefixForXmlSignatureNamespace != null && !prefixForXmlSignatureNamespace.isEmpty()) {
            el.setPrefix(prefixForXmlSignatureNamespace);
        }
        return el;
    }

    protected Element createElement(String localName, Document doc, Input input) {

        Element el = doc.createElementNS(findNamespace(input.getMessage()), localName);
        String p = findPrefix(input.getMessage());
        if (p != null && !p.isEmpty()) {
            el.setPrefix(p);
        }
        return el;
    }
}
