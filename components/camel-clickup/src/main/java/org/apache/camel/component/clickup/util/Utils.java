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

package org.apache.camel.component.clickup.util;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import jakarta.xml.bind.DatatypeConverter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {

    private static final Logger LOG = LoggerFactory.getLogger(Utils.class);

    private static final String HMAC_HASHING_ALGORITHM = "HmacSHA256";

    public static String computeMessageHMAC(String message, String key) {
        try {
            Mac mac = Mac.getInstance(HMAC_HASHING_ALGORITHM);
            mac.init(new SecretKeySpec(key.getBytes(), HMAC_HASHING_ALGORITHM));

            byte[] hash = mac.doFinal(message.getBytes());

            return DatatypeConverter.printHexBinary(hash);
        } catch (NoSuchAlgorithmException e) {
            LOG.debug(
                    "This exception should never occur: cannot find the hashing algorithm. {}", HMAC_HASHING_ALGORITHM);

            throw new RuntimeCamelException(e);
        } catch (InvalidKeyException e) {
            LOG.debug("This exception should never occur: the provided key is not valid. {}", key);

            throw new RuntimeCamelException(e);
        }
    }
}
