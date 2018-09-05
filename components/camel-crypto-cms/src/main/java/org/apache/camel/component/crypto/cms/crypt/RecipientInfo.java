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
package org.apache.camel.component.crypto.cms.crypt;

/**
 * Information about the receiver of an encrypted message used in
 * {@link EnvelopedDataEncryptor}. The RecipientInfo type depends on the key
 * management algorithm used for the recipient of an <code>EnvelopedData</code>
 * or <code>AuthenticatedData</code>. CMS provides three alternatives (see rfc5652):
 * <ul>
 * <li>key transport: the content-encryption key is encrypted with the public
 * key of the recipient. This technique id compatible to PKCS#7 when creating a
 * RecipientInfo for the public key of the recipient's certificate, identified
 * by issuer and serial number. CMS recommends to use RSA for encrypting the
 * content encryption key.
 * <li>key agreement: the recipient's public key and the sender's private key
 * are used to generate a symmetric key, then the content encryption key is
 * encrypted with the symmetric key. Each RecipientInfo of type may transfer the
 * encrypted content encryption key to one or more recipient using the same key
 * agreement algorithm and domain parameters for that algorithm. CMS recommends
 * to use ESDH with an ephemeral sender key.
 * <li>symmetric key-encryption keys: the content-encryption key is encrypted
 * with a previously distributed symmetric key-encryption key. The RecipientInfo
 * is using a CMS key wrap algorithm like Triple-DES key wrap or RC2 key wrap.
 * <li>password based encryption: the content-encryption key is encrypted with
 * key-encryption key derived from a password. The RecipientInfo is using a key
 * derivation algorithm like PBKDF2 as specified by <a href =
 * http://www.ietf.org/rfc/rfc2898.txt" target="_blank">RFC 2898</a> (PKCS#5)
 * and a key encryption algorithm like PWRI-KEK as specified by <a href =
 * http://www.ietf.org/rfc/rfc3211.txt" target="_blank">RFC 3211</a>.
 * <li>any other technique: based on private, user defined key management
 * techniques
 * </ul>
 * Currently we only support the "key transport" alternative. However in
 * preparation to support in future further types, we have introduced this
 * class.
 */
public interface RecipientInfo {

}
