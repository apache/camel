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
package org.apache.camel.component.iec60870;

import java.util.Arrays;
import java.util.Objects;

import org.eclipse.neoscada.protocol.iec60870.asdu.types.ASDUAddress;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.InformationObjectAddress;

public class ObjectAddress {
    int[] address;

    private ObjectAddress(final int[] address) {
        this.address = address;
    }

    public ObjectAddress(final int a1, final int a2, final int a3, final int a4, final int a5) {
        this.address = new int[] {a1, a2, a3, a4, a5};
    }

    @Override
    public String toString() {
        return String.format("%02d-%02d-%02d-%02d-%02d", this.address[0], this.address[1], this.address[2], this.address[3], this.address[4]);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(this.address);
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ObjectAddress other = (ObjectAddress)obj;
        if (!Arrays.equals(this.address, other.address)) {
            return false;
        }
        return true;
    }

    public static ObjectAddress valueOf(final ASDUAddress asduAddress, final InformationObjectAddress address) {
        Objects.requireNonNull(asduAddress);
        Objects.requireNonNull(address);

        final int[] a = asduAddress.toArray();
        final int[] b = address.toArray();

        return new ObjectAddress(a[0], a[1], b[0], b[1], b[2]);
    }

    public static ObjectAddress valueOf(final String address) {
        if (address == null || address.isEmpty()) {
            return null;
        }

        final String[] toks = address.split("-");
        if (toks.length != 5) {
            throw new IllegalArgumentException("Invalid address. Must have 5 octets.");
        }

        final int[] a = new int[toks.length];

        for (int i = 0; i < toks.length; i++) {
            final int v;
            try {
                v = Integer.parseInt(toks[i]);
            } catch (final NumberFormatException e) {
                throw new IllegalArgumentException("Address segment must be numeric", e);
            }

            if (v < 0 || v > 255) {
                throw new IllegalArgumentException(String.format("Address segment must be an octet, between 0 and 255 (is %s)", v));
            }

            a[i] = v;
        }

        return new ObjectAddress(a);
    }

    public ASDUAddress getASDUAddress() {
        return ASDUAddress.fromArray(new int[] {this.address[0], this.address[1]});
    }

    public InformationObjectAddress getInformationObjectAddress() {
        return InformationObjectAddress.fromArray(new int[] {this.address[2], this.address[3], this.address[4]});
    }
}
