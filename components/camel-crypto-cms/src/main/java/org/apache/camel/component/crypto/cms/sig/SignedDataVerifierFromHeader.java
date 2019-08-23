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
package org.apache.camel.component.crypto.cms.sig;

import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.crypto.cms.common.CryptoCmsConstants;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsFormatException;
import org.apache.camel.util.IOHelper;
import org.apache.commons.codec.binary.Base64InputStream;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.cms.CMSTypedStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies the signature contained in the header
 * {@link CryptoCmsConstants#CAMEL_CRYPTO_CMS_SIGNED_DATA}.
 */
public class SignedDataVerifierFromHeader extends SignedDataVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(SignedDataVerifierFromHeader.class);

    private SignedDataVerifierConfiguration conf;

    public SignedDataVerifierFromHeader(SignedDataVerifierConfiguration conf) {
        super(conf);
        this.conf = conf;
    }

    @Override
    public void process(Exchange exchange) throws Exception { // NOPMD see
                                                              // method
                                                              // processSignedDataHader

        InputStream signature = exchange.getIn().getHeader(CryptoCmsConstants.CAMEL_CRYPTO_CMS_SIGNED_DATA, InputStream.class);
        if (signature == null) {
            LOG.debug("No signed data found in header {}. Assuming signed data contained in message body", CryptoCmsConstants.CAMEL_CRYPTO_CMS_SIGNED_DATA);
            super.process(exchange);
        } else {
            LOG.debug("Signed data header {} found.", CryptoCmsConstants.CAMEL_CRYPTO_CMS_SIGNED_DATA);
            processSignedDataHeader(exchange, signature);

            // remove header
            exchange.getIn().removeHeader(CryptoCmsConstants.CAMEL_CRYPTO_CMS_SIGNED_DATA);
        }
    }

    protected void processSignedDataHeader(Exchange exchange, InputStream signature) throws Exception { // NOPMD
        // all exceptions must be caught and re-thrown in order to make a
        // clean-up, see code below
        if (conf.isSignedDataHeaderBase64()) {
            signature = new Base64InputStream(signature);
        }

        InputStream stream = exchange.getIn().getMandatoryBody(InputStream.class);
        try {
            // lets setup the out message before we invoke the dataFormat
            // so that it can mutate it if necessary
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());

            if (conf.isFromBase64()) {
                stream = new Base64InputStream(stream);
            }
            unmarshalInternal(stream, signature, exchange);
        } catch (Exception e) {
            // remove OUT message, as an exception occurred
            exchange.setOut(null);
            throw e;
        } finally {
            IOHelper.close(stream, "input stream");
        }
    }

    protected void unmarshalInternal(InputStream is, InputStream signature, Exchange exchange) throws Exception {

        CMSSignedDataParser sp;
        try {
            sp = new CMSSignedDataParser(new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build(), new CMSTypedStream(is), signature);
        } catch (CMSException e) {
            throw new CryptoCmsFormatException(getFormatErrorMessage(), e);
        }
        try {
            // content must be read in order to calculate the hash for the
            // signature
            sp.getSignedContent().drain();
        } catch (NullPointerException e) { // nullpointer exception is thrown
                                           // when the signed content is missing
            throw getContentMissingException(e);
        }

        LOG.debug("Signed data found");
        debugLog(sp);
        verify(sp, exchange);
    }

}
