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
package org.apache.camel.component.as2.api;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.cms.jcajce.ZlibCompressor;
import org.bouncycastle.operator.OutputCompressor;

public enum AS2CompressionAlgorithm {
    ZLIB(new ZlibCompressor());

    private final OutputCompressor outputCompressor;

    private AS2CompressionAlgorithm(OutputCompressor outputCompressor) {
        this.outputCompressor = outputCompressor;
    }

    public String getAlgorithmName() {
        return this.name();
    }

    public ASN1ObjectIdentifier getAlgorithmOID() {
        return outputCompressor.getAlgorithmIdentifier().getAlgorithm();
    }

    public OutputCompressor getOutputCompressor() {
        return outputCompressor;
    }

}
