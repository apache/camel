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
package org.apache.camel.component.xmlsecurity.util;

import java.util.HashSet;
import java.util.Set;

import javax.xml.crypto.dsig.CanonicalizationMethod;

import org.apache.camel.component.xmlsecurity.api.XmlSignatureChecker;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureInvalidException;

/**
 * Checks that root element local name is "Signature" and the root element
 * namespace is "http://www.w3.org/2000/09/xmldsig#". If the checks fails then a
 * {@link XmlSignatureInvalidException} is thrown.
 */
public class EnvelopingXmlSignatureChecker implements XmlSignatureChecker {

    private static final Set<String> ALLOWED_TRANSFORM_ALGORITHMS = new HashSet<>(4);

    static {
        ALLOWED_TRANSFORM_ALGORITHMS.add(CanonicalizationMethod.INCLUSIVE);
        ALLOWED_TRANSFORM_ALGORITHMS.add(CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS);
        ALLOWED_TRANSFORM_ALGORITHMS.add(CanonicalizationMethod.EXCLUSIVE);
        ALLOWED_TRANSFORM_ALGORITHMS.add(CanonicalizationMethod.EXCLUSIVE_WITH_COMMENTS);
    }

    @Override
    public void checkBeforeCoreValidation(Input input) throws Exception {

        if (!"Signature".equals(input.getMessageBodyDocument().getDocumentElement().getLocalName())) {
            throw new XmlSignatureInvalidException(
                "XML signature is not enveloping. Only enveloping XML signatures are allowed.");
        }
    }

}
