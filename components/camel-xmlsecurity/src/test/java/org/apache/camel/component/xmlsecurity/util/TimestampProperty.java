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
package org.apache.camel.component.xmlsecurity.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;

import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureProperties;
import javax.xml.crypto.dsig.SignatureProperty;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLObject;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;

import org.w3c.dom.Document;

import org.apache.camel.component.xmlsecurity.api.XmlSignatureHelper;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureProperties;


/**
 * Example for a XmlSignatureProperties implementation which adds a timestamp
 * signature property.
 */
public class TimestampProperty implements XmlSignatureProperties {

    @Override
    public Output get(Input input) throws Exception {

        Transform transform = input.getSignatureFactory().newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null);
        Reference ref = input.getSignatureFactory().newReference("#propertiesObject",
                input.getSignatureFactory().newDigestMethod(input.getContentDigestAlgorithm(), null), Collections.singletonList(transform),
                null, null);

        String doc2 = "<ts:timestamp xmlns:ts=\"http:/timestamp\">" + System.currentTimeMillis() + "</ts:timestamp>";
        InputStream is = new ByteArrayInputStream(doc2.getBytes("UTF-8"));
        Document doc = XmlSignatureHelper.newDocumentBuilder(Boolean.TRUE).parse(is);
        DOMStructure structure = new DOMStructure(doc.getDocumentElement());

        SignatureProperty prop = input.getSignatureFactory().newSignatureProperty(Collections.singletonList(structure),
                input.getSignatureId(), "property");
        SignatureProperties properties = input.getSignatureFactory().newSignatureProperties(Collections.singletonList(prop), "properties");
        XMLObject propertiesObject = input.getSignatureFactory().newXMLObject(Collections.singletonList(properties), "propertiesObject",
                null, null);

        XmlSignatureProperties.Output result = new Output();
        result.setReferences(Collections.singletonList(ref));
        result.setObjects(Collections.singletonList(propertiesObject));

        return result;
    }

}
