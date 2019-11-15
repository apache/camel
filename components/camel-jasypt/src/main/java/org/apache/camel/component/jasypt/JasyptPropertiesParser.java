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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.component.properties.DefaultPropertiesParser;
import org.apache.camel.component.properties.PropertiesLookup;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;

import static java.lang.String.format;

/**
 * A {@link org.apache.camel.component.properties.PropertiesParser} which is using
 * &nbsp;<a href="http://www.jasypt.org/">Jasypt</a> to decrypt encrypted values.
 * <p/>
 * The parts of the values which should be decrypted must be enclosed in the prefix and suffix token.
 */
public class JasyptPropertiesParser extends DefaultPropertiesParser {

    public static final String JASYPT_PREFIX_TOKEN = "ENC(";
    public static final String JASYPT_SUFFIX_TOKEN = ")";

    private static final String JASYPT_REGEX = JASYPT_PREFIX_TOKEN.replace("(", "\\(") + "(.+?)" + JASYPT_SUFFIX_TOKEN.replace(")", "\\)");
    private static final Pattern PATTERN = Pattern.compile(JASYPT_REGEX);

    private StringEncryptor encryptor;
    private String password;
    private String algorithm;

    public JasyptPropertiesParser() {
    }

    @Override
    public String parseProperty(String key, String value, PropertiesLookup properties) {
        log.trace(format("Parsing property '%s=%s'", key, value));
        if (value != null) {
            initEncryptor();
            Matcher matcher = PATTERN.matcher(value);
            while (matcher.find()) {
                log.trace(format("Decrypting part '%s'", matcher.group(0)));
                String decrypted = encryptor.decrypt(matcher.group(1));
                value = value.replace(matcher.group(0), decrypted);
            }
        }
        return value;
    }

    private synchronized void initEncryptor() {
        if (encryptor == null) {
            StringHelper.notEmpty("password", password);
            StandardPBEStringEncryptor pbeStringEncryptor = new StandardPBEStringEncryptor();
            pbeStringEncryptor.setPassword(password);
            if (algorithm != null) {
                pbeStringEncryptor.setAlgorithm(algorithm);
                log.debug(format("Initialized encryptor using %s algorithm and provided password", algorithm));
            } else {
                log.debug("Initialized encryptor using default algorithm and provided password");
            }
            encryptor = pbeStringEncryptor;
        }
    }

    public void setEncryptor(StringEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public void setPassword(String password) {
        // lookup password as either environment or JVM system property
        if (password.startsWith("sysenv:")) {
            password = System.getenv(StringHelper.after(password, "sysenv:"));
        }
        if (ObjectHelper.isNotEmpty(password) && password.startsWith("sys:")) {
            password = System.getProperty(StringHelper.after(password, "sys:"));
        }
        this.password = password;
    }
}
