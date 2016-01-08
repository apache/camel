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
package org.apache.camel.component.braintree;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.Environment;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component configuration for Braintree component.
 */
@UriParams
public class BraintreeConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(BraintreeConfiguration.class);

    private static final String ENVIRONMENT = "environment";
    private static final String MERCHANT_ID = "merchant_id";
    private static final String PUBLIC_KEY = "public_key";
    private static final String PRIVATE_KEY = "private_key";

    @UriParam
    @Metadata(required = "true")
    private String environment;

    @UriParam
    @Metadata(required = "true")
    private String merchantId;

    @UriParam
    @Metadata(required = "true")
    private String publicKey;

    @UriParam
    @Metadata(required = "true")
    private String privateKey;


    /**
     * @return the environment
     */
    public String getEnvironment() {
        return ObjectHelper.notNull(environment, ENVIRONMENT);
    }

    /**
     * @param environment the environment Either SANDBOX or PRODUCTION
     */
    public void setEnvironment(String environment) {
        this.environment = environment;
    }


    /**
     * @return the merchant id
     */
    public String getMerchantId() {
        return ObjectHelper.notNull(merchantId, MERCHANT_ID);
    }

    /**
     * @param merchantId the merchant id provided by Braintree.
     */
    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }


    /**
     * @return the public key
     */
    public String getPublicKey() {
        return ObjectHelper.notNull(publicKey, PUBLIC_KEY);
    }

    /**
     * @param publicKey the public key provided by Braintree.
     */
    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }


    /**
     * @return the private key
     */
    public String getPrivateKey() {
        return ObjectHelper.notNull(privateKey, PRIVATE_KEY);
    }

    /**
     * @param privateKey the private key provided by Braintree.
     */
    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }


    /**
     * Helper method to get and Environment object from its name
     *
     * @return the environment
     */
    private Environment getBraintreeEnvironment() {
        String name = getEnvironment();

        if (StringUtils.equalsIgnoreCase("development", name)) {
            return Environment.DEVELOPMENT;
        }

        if (StringUtils.equalsIgnoreCase("sandbox", name)) {
            return Environment.SANDBOX;
        }

        if (StringUtils.equalsIgnoreCase("production", name)) {
            return Environment.PRODUCTION;
        }

        throw new IllegalArgumentException(String.format(
            "Environment should be  development, sandbox or production, got %s", name));
    }

    /**
     * Contruct a BraintreeGateway from configuration
     *
     * @return a braintree gateway
     */
    BraintreeGateway newBraintreeGateway() {
        return new BraintreeGateway(
            getBraintreeEnvironment(),
            getMerchantId(),
            getPublicKey(),
            getPrivateKey());
    }

    /*
    public void validate() {
        if (ObjectHelper.isEmpty(environment)
            || ObjectHelper.isEmpty(merchantId)
            || ObjectHelper.isEmpty(publicKey)
            || ObjectHelper.isEmpty(privateKey)) {
            throw new IllegalArgumentException(String.format(
                "Missing required properties %s, %s, %s, %s", ENVIRONMENT, MERCHANT_ID, PUBLIC_KEY, PRIVATE_KEY));
        }
    }
    */
}
