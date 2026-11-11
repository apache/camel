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

import java.io.IOException;
import java.util.List;

import io.spiffe.bundle.x509bundle.X509Bundle;
import io.spiffe.exception.BundleNotFoundException;
import io.spiffe.exception.InvalidSpiffeIdException;
import io.spiffe.spiffeid.SpiffeId;
import io.spiffe.spiffeid.TrustDomain;
import io.spiffe.svid.x509svid.X509Svid;
import io.spiffe.workloadapi.X509Source;
import org.apache.camel.util.ObjectHelper;

/** Exact local identity selection shared by the producer and the rotating TLS source. */
final class SpiffeX509SvidSelector {
    private SpiffeX509SvidSelector() {
    }

    static SpiffeId parse(String configured) {
        if (ObjectHelper.isEmpty(configured)) {
            return null;
        }
        try {
            return SpiffeId.parse(configured.trim());
        } catch (InvalidSpiffeIdException e) {
            throw new IllegalArgumentException("localSpiffeId must be an exact SPIFFE ID", e);
        }
    }

    static X509Svid select(List<X509Svid> svids, SpiffeId requested) {
        if (svids != null) {
            for (X509Svid svid : svids) {
                if (svid != null && requested.equals(svid.getSpiffeId())) {
                    return svid;
                }
            }
        }
        // The SDK picker must publish a missing selected SVID on removal, rather than retain its previous snapshot.
        return null;
    }

    static X509Svid require(X509Svid svid, SpiffeId requested) {
        if (svid == null || !requested.equals(svid.getSpiffeId())) {
            throw new IllegalStateException(
                    "Configured local SPIFFE ID " + requested + " is not present in the Workload API X.509 context");
        }
        return svid;
    }

    static X509Source guard(X509Source source, SpiffeId requested) {
        return new X509Source() {
            @Override
            public X509Svid getX509Svid() {
                return require(source.getX509Svid(), requested);
            }

            @Override
            public X509Bundle getBundleForTrustDomain(TrustDomain trustDomain) throws BundleNotFoundException {
                return source.getBundleForTrustDomain(trustDomain);
            }

            @Override
            public void close() throws IOException {
                source.close();
            }
        };
    }
}
