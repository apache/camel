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
package org.apache.camel.component.corda;

import java.util.ArrayList;
import java.util.List;

import net.corda.core.serialization.SerializationWhitelist;

public class IntegrationWhiteList implements SerializationWhitelist {

    @Override
    public List<Class<?>> getWhitelist() {
        List list = new ArrayList();
//        list.add(RSAPublicKeyImpl.class);
//        list.add(AlgorithmId.class);
//        list.add(ObjectIdentifier.class);
//        list.add(BitArray.class);
//        list.add(BigInteger.class);
        return list;
    }
}
