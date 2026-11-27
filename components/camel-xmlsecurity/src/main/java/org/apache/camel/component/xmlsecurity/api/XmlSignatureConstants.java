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

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public final class XmlSignatureConstants {
    // The schemes
    public static final String SCHEME_VERIFIER = "xmlsecurity-verify";
    public static final String SCHEME_SIGN = "xmlsecurity-sign";
    @Metadata(javaType = "Boolean", applicableFor = SCHEME_SIGN,
              description = "Whether the message body is plain text instead of an XML document. Replaces the plainText option."
                            + " Plain text can only get an enveloping signature.")
    public static final String HEADER_MESSAGE_IS_PLAIN_TEXT = "CamelXmlSignatureMessageIsPlainText";

    @Metadata(javaType = "String", applicableFor = SCHEME_SIGN,
              description = "The character encoding that the signer decodes a plain text message body with. Replaces the"
                            + " plainTextEncoding option.")
    public static final String HEADER_PLAIN_TEXT_ENCODING = "CamelXmlSignaturePlainTextEncoding";

    @Metadata(javaType = "Boolean",
              description = "Whether to leave out the XML declaration of the output body. Replaces the omitXmlDeclaration option."
                            + " The verifier reads it through the default xmlSignature2Message, and it has no effect when the output"
                            + " of the verifier is text.")
    public static final String HEADER_OMIT_XML_DECLARATION = "CamelXmlSignatureOmitXmlDeclaration";
    @Metadata(description = "The URI of the reference to the signed content of an enveloped signature. Replaces the"
                            + " contentReferenceUri option. Not used for an enveloping or detached signature.",
              javaType = "String", applicableFor = SCHEME_SIGN)
    public static final String HEADER_CONTENT_REFERENCE_URI = "CamelXmlSignatureContentReferenceUri";
    @Metadata(description = "The Type attribute of the reference to the signed content. Replaces the contentReferenceType option.",
              javaType = "String", applicableFor = SCHEME_SIGN)
    public static final String HEADER_CONTENT_REFERENCE_TYPE = "CamelXmlSignatureContentReferenceType";
    @Metadata(description = "The XML schema that the message body is validated against while it is parsed. Replaces the"
                            + " schemaResourceUri option.",
              javaType = "String")
    public static final String HEADER_SCHEMA_RESOURCE_URI = "CamelXmlSignatureSchemaResourceUri";
    @Metadata(description = "A java.util.List of javax.xml.crypto.dsig.spec.XPathFilterParameterSpec that selects the ID"
                            + " attributes of the elements that get a detached signature. Replaces the xpathsToIdAttributes option."
                            + " Only the signer reads it.",
              javaType = "String")
    public static final String HEADER_XPATHS_TO_ID_ATTRIBUTES = "CamelXmlSignatureXpathsToIdAttributes";

    @Metadata(javaType = "String", applicableFor = SCHEME_SIGN,
              description = "A comma-separated list of the URIs of the transforms of the reference to the signed content, for"
                            + " example"
                            + " http://www.w3.org/2000/09/xmldsig#enveloped-signature,http://www.w3.org/TR/2001/REC-xml-c14n-20010315."
                            + " Replaces the transformMethods option; for an enveloped signature, the enveloped-signature transform"
                            + " is then not added automatically. Only transforms without parameters are possible, so not XSLT, XPath or XPath Filter"
                            + " 2.0.")
    public static final String HEADER_TRANSFORM_METHODS = "CamelXmlSignatureTransformMethods";

    /*------------------------- headers for XAdES signer ----------------------------------------------------------*/
    @Metadata(description = "The Id attribute of the XAdES QualifyingProperties element. Only used by the signer when the"
                            + " properties option is an XAdESSignatureProperties. If not set, the element has no Id attribute.",
              javaType = "String")
    public static final String HEADER_XADES_QUALIFYING_PROPERTIES_ID = "CamelXmlSignatureXAdESQualifyingPropertiesId";

    @Metadata(description = "The Id attribute of the XAdES SignedDataObjectProperties element. Only used by the signer when the"
                            + " properties option is an XAdESSignatureProperties. If not set, the element has no Id attribute.",
              javaType = "String")
    public static final String HEADER_XADES_SIGNED_DATA_OBJECT_PROPERTIES_ID
            = "CamelXmlSignatureXAdESSignedDataObjectPropertiesId";

    @Metadata(description = "The Id attribute of the XAdES SignedSignatureProperties element. Only used by the signer when the"
                            + " properties option is an XAdESSignatureProperties. If not set, the element has no Id attribute.",
              javaType = "String")
    public static final String HEADER_XADES_SIGNED_SIGNATURE_PROPERTIES_ID
            = "CamelXmlSignatureXAdESSignedSignaturePropertiesId";

    @Metadata(description = "The text of the Encoding element of the XAdES DataObjectFormat element. Only used by the signer when"
                            + " the properties option is an XAdESSignatureProperties that adds a DataObjectFormat element. If not"
                            + " set, there is no Encoding element.",
              javaType = "String")
    public static final String HEADER_XADES_DATA_OBJECT_FORMAT_ENCODING = "CamelXmlSignatureXAdESDataObjectFormatEncoding";

    @Metadata(description = "The namespace of the XAdES elements, which selects the XAdES version:"
                            + " http://uri.etsi.org/01903/v1.1.1#, http://uri.etsi.org/01903/v1.2.2# or"
                            + " http://uri.etsi.org/01903/v1.3.2#. Replaces the namespace of the XAdESSignatureProperties of the"
                            + " properties option. Only used by the signer.",
              javaType = "String")
    public static final String HEADER_XADES_NAMESPACE = "CamelXmlSignatureXAdESNamespace";

    @Metadata(description = "The namespace prefix of the XAdES elements; an empty value means no prefix. Replaces the prefix of"
                            + " the XAdESSignatureProperties of the properties option, which is etsi by default. Only used by the"
                            + " signer.",
              javaType = "String")
    public static final String HEADER_XADES_PREFIX = "CamelXmlSignatureXAdESPrefix";
    @Metadata(description = "The character encoding of the output body, which the signer and the default xmlSignature2Message of"
                            + " the verifier set when outputXmlEncoding is set. Not removed by clearHeaders.",
              javaType = "String")
    public static final String CHARSET_NAME = Exchange.CHARSET_NAME;

    private XmlSignatureConstants() {
        // no instance
    }

}
