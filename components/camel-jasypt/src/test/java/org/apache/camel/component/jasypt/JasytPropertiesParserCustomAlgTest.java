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
package org.apache.camel.component.jasypt;

import org.jasypt.iv.RandomIvGenerator;
import org.jasypt.salt.RandomSaltGenerator;
import org.junit.jupiter.api.BeforeEach;

public class JasytPropertiesParserCustomAlgTest extends JasyptPropertiesParserTest {

    @BeforeEach
    public void before() {
        knowDecrypted = "tigertigertiger";
        knownEncrypted = "ENC(LuCBTHaY1G6XHRwp63teshi/LbFRzpPtq5j8SNpJgv1yn9D25py+xHNGjXEMnf/J)";

        encryptor.setAlgorithm("PBEWithHmacSHA256AndAES_256");
        encryptor.setSaltGenerator(new RandomSaltGenerator("SHA1PRNG"));
        encryptor.setIvGenerator(new RandomIvGenerator("SHA1PRNG"));
        encryptor.setPassword(knownPassword);

        jasyptPropertiesParser.setAlgorithm("PBEWithHmacSHA256AndAES_256");
        jasyptPropertiesParser.setRandomSaltGeneratorAlgorithm("SHA1PRNG");
        jasyptPropertiesParser.setRandomIvGeneratorAlgorithm("SHA1PRNG");
        jasyptPropertiesParser.setEncryptor(encryptor);
    }
}
