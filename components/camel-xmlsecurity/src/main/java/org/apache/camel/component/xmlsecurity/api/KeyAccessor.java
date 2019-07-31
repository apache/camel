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

import javax.xml.crypto.KeySelector;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;

import org.w3c.dom.Node;

import org.apache.camel.Message;

/**
 * Returns the key selector and the optional KeyInfo instance for signing an XML
 * document. There is a default implementation {@link DefaultKeySelector}.
 * <p>
 * The XML signature generator will first call {@link #getKeySelector(Message)}
 * and then {@link KeyAccessor#getKeyInfo(Message, Node, KeyInfoFactory)}.
 */
public interface KeyAccessor {

    /**
     * Returns the key selector which determines the key for signing the XML
     * document. The method is called every time a XML document is signed.
     * 
     * If <code>null</code> is returned the XML signature generator will throw a
     * {@link XmlSignatureNoKeyException}.
     * 
     * @param message
     *            the incoming message, from which you can read headers to
     *            configure the key selector, for example, a header could
     *            contain a private key for the key selector
     * @return key selector, must not be <code>null</code>
     * @throws Exception
     *             if an error occurs
     */
    KeySelector getKeySelector(Message message) throws Exception;

    /**
     * Returns the optional key info to be incorporated into the XML signature.
     * If <code>null</code> is returned, no key info element is created. You can
     * create a key info instance via the key info factory.
     * <p>
     * This method will be called several times if several signatures shall be
     * created for the XML document; for each signature a call is executed. This
     * allows you to create different key info IDs.
     * 
     * @param message
     *            incoming message, from which you can read headers, for
     *            example, there could be a header which contains the public key
     *            or certificate for the key info
     * @param messageBody
     *            the message body as DOM node. If the message body is plain
     *            text then the node will be a text node. If the message body is
     *            a XML document, then the node is the root element.
     * @param keyInfoFactory
     *            key info factory for creating the KeyInfo instance
     * @return key info, can be <code>null</code>
     * @throws Exception
     *             if an error occurs
     */
    KeyInfo getKeyInfo(Message message, Node messageBody, KeyInfoFactory keyInfoFactory) throws Exception;

}
