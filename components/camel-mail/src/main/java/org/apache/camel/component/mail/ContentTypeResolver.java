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

/**
 * Resolver to determine Content-Type for file attachments.
 * <p/>
 * Strategy introduced to work around mail providers having problems with this such as geronimo mail jars.
 * <p/>
 * Note using SUN mail jar have no problem with resolving Content-Type based on file attachments. This resolver
 * is thus only needed to work around mail providers having bugs or when you a new mime type is unknown by the
 * mail provider allowing you to determine it.
 *
 * @version 
 */
public interface ContentTypeResolver {

    /**
     * Resolves the mime content-type based on the attachment file name.
     * <p/>
     * Return <tt>null</tt> if you cannot resolve a content type or want to rely on the mail provider
     * to resolve it for you.
     * <p/>
     * The returned value should only be the mime part of the ContentType header, for example:
     * <tt>image/jpeg</tt> should be returned. Camel will add the remaining <tt>; name=FILENAME</tt>.
     *
     * @param fileName  the attachment file name
     * @return the Content-Type or <tt>null</tt> to rely on the mail provider
     */
    String resolveContentType(String fileName);
}
