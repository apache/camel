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
package org.apache.camel.component.mail;

import javax.mail.BodyPart;

/**
 * Resolver to determine Content-Transfer-Encoding for file attachments.
 * <br/>
 * Normally this will be determined automatically, this resolver can be used to
 * override this behavior.
 */
public interface AttachmentsContentTransferEncodingResolver {

    /**
     * Resolves the content-transfer-encoding.
     * <p/>
     * Return <tt>null</tt> if you cannot resolve a content-transfer-encoding or
     * want to rely on the mail provider to resolve it for you.
     *
     * @param messageBodyPart the body part
     * @return the content-transfer-encoding or <tt>null</tt> to rely on the mail provider
     */
    String resolveContentTransferEncoding(BodyPart messageBodyPart);

}
