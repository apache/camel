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
package org.apache.camel.component.jasypt;

import org.apache.camel.component.properties.DefaultPropertiesParser;
import org.apache.camel.util.ObjectHelper;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;

/**
 * A {@link org.apache.camel.component.properties.PropertiesParser} which is using Jasypt
 * to decrypt any encrypted values.
 * <p/>
 * The values must be enclosed in the prefix and suffix token.
 *
 * @version $Revision$
 */
public class JasyptPropertiesParser extends DefaultPropertiesParser {

    public static final String JASYPT_PREFIX_TOEKN = "ENC(";
    public static final String JASYPT_SUFFIX_TOEKN = ")";

    // TODO: A JasyptComponent we can leverage instead of directly from here
    private StandardPBEStringEncryptor encryptor;
    private String password;
    private String algorithm;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public synchronized StandardPBEStringEncryptor getEncryptor() {
        if (encryptor == null) {
            encryptor = new StandardPBEStringEncryptor();
            encryptor.setPassword(password);
            if (algorithm != null) {
                encryptor.setAlgorithm(algorithm);
            }
        }
        return encryptor;
    }

    @Override
    public String parsePropertyValue(String value) {
        // check if the value is using the tokens
        String text = ObjectHelper.between(value, JASYPT_PREFIX_TOEKN, JASYPT_SUFFIX_TOEKN);
        if (text == null) {
            // not encrypted
            return value;
        } else {
            // do not log the decrypted text as it could be sensitive information such as a password
            return getEncryptor().decrypt(text);
        }
    }

}
