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
package org.apache.camel.component.apns.util;

import java.util.Random;

import com.notnoop.apns.internal.ApnsFeedbackParsingUtilsAcessor;
import com.notnoop.apns.internal.Utilities;
import com.notnoop.apns.utils.FixedCertificates;

import org.apache.camel.CamelContext;
import org.apache.camel.component.apns.factory.ApnsServiceFactory;

public final class ApnsUtils {

    private static Random random = new Random();

    private ApnsUtils() {
        super();
    }

    public static byte[] createRandomDeviceTokenBytes() {
        byte[] deviceTokenBytes = new byte[32];
        random.nextBytes(deviceTokenBytes);

        return deviceTokenBytes;
    }

    public static String encodeHexToken(byte[] deviceTokenBytes) {
        String deviceToken = Utilities.encodeHex(deviceTokenBytes);

        return deviceToken;
    }

    public static ApnsServiceFactory createDefaultTestConfiguration(CamelContext camelContext) {
        ApnsServiceFactory apnsServiceFactory = new ApnsServiceFactory(camelContext);

        apnsServiceFactory.setFeedbackHost(FixedCertificates.TEST_HOST);
        apnsServiceFactory.setFeedbackPort(FixedCertificates.TEST_FEEDBACK_PORT);
        apnsServiceFactory.setGatewayHost(FixedCertificates.TEST_HOST);
        apnsServiceFactory.setGatewayPort(FixedCertificates.TEST_GATEWAY_PORT);
        // apnsServiceFactory.setCertificatePath("classpath:/" +
        // FixedCertificates.CLIENT_STORE);
        // apnsServiceFactory.setCertificatePassword(FixedCertificates.CLIENT_PASSWD);
        apnsServiceFactory.setSslContext(FixedCertificates.clientContext());

        return apnsServiceFactory;
    }

    public static byte[] generateFeedbackBytes(byte[] deviceTokenBytes) {
        byte[] feedbackBytes = ApnsFeedbackParsingUtilsAcessor.pack(
        /* time_t */new byte[] {0, 0, 0, 0},
        /* length */new byte[] {0, 32},
        /* device token */deviceTokenBytes);

        return feedbackBytes;
    }

}
