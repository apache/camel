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
package org.apache.camel.maven;

import java.util.Arrays;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

public class HashHelper {

    private final Hasher hasher = Hashing.murmur3_128().newHasher();

    public HashHelper hash(String field, Object f) {
        hasher.putUnencodedChars(field);
        return hash(f);
    }

    public HashHelper hash(Object f) {
        if (f != null) {
            if (f.getClass().isArray()) {
                f = Arrays.asList((Object[]) f);
            }
            if (f instanceof String) {
                hasher.putUnencodedChars((String) f);
            } else if (f instanceof Boolean) {
                hasher.putBoolean((Boolean) f);
            } else if (f instanceof Iterable) {
                for (Object a : (Iterable<?>) f) {
                    hash(a);
                }
            } else if (f instanceof ApiMethodAlias) {
                ApiMethodAlias apiMethodAlias = (ApiMethodAlias) f;
                hash("ApiMethodAlias");
                hash("methodPattern", apiMethodAlias.getMethodPattern());
                hash("methodAlias", apiMethodAlias.getMethodAlias());
            } else if (f instanceof ApiProxy) {
                ApiProxy apiProxy = (ApiProxy) f;
                hash("ApiProxy");
                hash("apiName", apiProxy.getApiName());
                hash("apiDescription", apiProxy.getApiDescription());
                hash("consumerOnly", apiProxy.isConsumerOnly());
                hash("producerOnly", apiProxy.isProducerOnly());
                hash("fromJavasource", apiProxy.getFromJavasource());
                hash("substitutions", apiProxy.getSubstitutions());
                hash("excludeConfigNames", apiProxy.getExcludeConfigNames());
                hash("excludeConfigTypes", apiProxy.getExcludeConfigTypes());
                hash("extraOptions", apiProxy.getExtraOptions());
                hash("nullableOptions", apiProxy.getNullableOptions());
                hash("classPrefix", apiProxy.getClassPrefix());
                hash("aliases", apiProxy.getAliases());
            } else if (f instanceof ExtraOption) {
                ExtraOption extraOption = (ExtraOption) f;
                hash("ExtraOption");
                hash("type", extraOption.getType());
                hash("name", extraOption.getName());
                hash("description", extraOption.getDescription());
            } else if (f instanceof FromJavasource) {
                FromJavasource fromJavasource = (FromJavasource) f;
                hash("FromJavasource");
                hash("excludePackages", fromJavasource.getExcludePackages());
                hash("excludeClasses", fromJavasource.getExcludeClasses());
                hash("includeMethods", fromJavasource.getIncludeMethods());
                hash("excludeMethods", fromJavasource.getExcludeMethods());
                hash("includeStaticMethods", fromJavasource.getIncludeStaticMethods());
            } else if (f instanceof SignatureModel) {
                SignatureModel signatureModel = (SignatureModel) f;
                hash("SignatureModel");
                hash("apiName", signatureModel.getApiName());
                hash("apiDescription", signatureModel.getApiDescription());
                hash("methodDescription", signatureModel.getMethodDescription());
                hash("signature", signatureModel.getSignature());
                hash("parameterDescriptions", signatureModel.getMethodDescription());
                hash("parameterTypes", signatureModel.getParameterTypes());
            } else if (f instanceof Substitution) {
                Substitution substitution = (Substitution) f;
                hash("Substitution");
                hash("method", substitution.getMethod());
                hash("argName", substitution.getArgName());
                hash("argType", substitution.getArgType());
                hash("replacement", substitution.getReplacement());
                hash("replaceWithType", substitution.isReplaceWithType());
            } else {
                throw new UnsupportedOperationException();
            }
        }
        return this;
    }

    public String toString() {
        return hasher.hash().toString();
    }

}
