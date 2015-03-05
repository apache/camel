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
package org.apache.camel.component.gae.auth;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

import com.google.gdata.util.common.util.Base64;
import org.apache.camel.CamelContext;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ResourceHelper;

/**
 * A Java PKCS#8-specific key loader.
 */
public class GAuthPk8Loader implements GAuthKeyLoader {

    private static final String BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String END   = "-----END PRIVATE KEY-----";

    private CamelContext camelContext;
    private String keyLocation;

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Sets the location of the PKCS#8 file that contains a private key.
     */
    public void setKeyLocation(String keyLocation) {
        this.keyLocation = keyLocation;
    }

    /**
     * Loads a private key from a PKCS#8 file.
     */
    public PrivateKey loadPrivateKey() throws Exception {
        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext().getClassResolver(), keyLocation);
        String str;
        try {
            str = getCamelContext().getTypeConverter().mandatoryConvertTo(String.class, is);
        } finally {
            IOHelper.close(is);
        }

        // replace line feeds from windows to unix style
        if (!System.lineSeparator().equals("\n")) {
            str = str.replaceAll(System.lineSeparator(), "\n");
        }

        if (str.contains(BEGIN) && str.contains(END)) {
            str = str.substring(BEGIN.length(), str.lastIndexOf(END));
        }

        byte[] decoded = Base64.decode(str);

        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }
    
}
