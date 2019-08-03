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
package org.apache.camel.converter.crypto;

import org.junit.Ignore;

@Ignore("Requires JCE unlimited strength jurisdiction policy files on CI server")
public class PGPDataFormatElGamalTest extends PGPDataFormatTest {
    @Override
    protected String getKeyFileName() {
        return "org/apache/camel/component/crypto/pubring-ElGamal.gpg";
    }
    
    @Override
    protected String getKeyFileNameSec() {
        return "org/apache/camel/component/crypto/secring-ElGamal.gpg";
    }

}
