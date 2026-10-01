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
package org.apache.camel.component.spiffe;

/**
 * What the {@code fetchX509Svid} operation puts in the message body.
 * <p/>
 * The SPIFFE ID and expiry are always exposed as the {@code CamelSpiffeSpiffeId} and {@code CamelSpiffeExpiry} headers,
 * so a route that only needs its identity can use the default {@link #chain} (or {@link #id}) and never handle the
 * private key.
 */
public enum SpiffeX509Response {

    /**
     * The full {@link io.spiffe.svid.x509svid.X509Svid}, which carries the private key. Needed for programmatic mTLS;
     * opt in to it rather than getting the key by default.
     */
    svid,

    /**
     * The X.509 certificate chain only ({@code List<java.security.cert.X509Certificate>}), without the private key.
     * This is the default.
     */
    chain,

    /**
     * Leaves the message body untouched; the identity is available only through the headers.
     */
    id
}
