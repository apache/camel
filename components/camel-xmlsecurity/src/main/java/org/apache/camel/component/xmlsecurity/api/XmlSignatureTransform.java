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

import java.security.spec.AlgorithmParameterSpec;

import javax.xml.crypto.AlgorithmMethod;

/**
 * Transform and canonicalization algorithms with their parameters.
 */
public class XmlSignatureTransform implements AlgorithmMethod {

    private String algorithm;

    private AlgorithmParameterSpec parameterSpec;

    public XmlSignatureTransform() {

    }

    public XmlSignatureTransform(String algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public AlgorithmParameterSpec getParameterSpec() {
        return parameterSpec;
    }

    @Override
    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public void setParameterSpec(AlgorithmParameterSpec parameterSpec) {
        this.parameterSpec = parameterSpec;
    }

}
