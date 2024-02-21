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

package org.apache.camel.test.infra.ftp.services.embedded;

import java.security.NoSuchAlgorithmException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FtpsUtil {
    private static final Logger LOG = LoggerFactory.getLogger(FtpsUtil.class);

    private static boolean checked;
    private static boolean hasRequiredAlgorithms;

    private FtpsUtil() {

    }

    public static boolean hasRequiredAlgorithms() {
        if (!checked) {
            hasRequiredAlgorithms = doCheck();
        }

        return hasRequiredAlgorithms;
    }

    private static boolean doCheck() {
        LOG.debug("Checking if the system has the required algorithms for the test execution");
        try {
            KeyManagerFactory.getInstance("SunX509");
            TrustManagerFactory.getInstance("SunX509");

            return true;
        } catch (NoSuchAlgorithmException e) {
            String name = System.getProperty("os.name");
            String message = e.getMessage();

            LOG.warn("SunX509 is not available on this platform [{}] Testing is skipped! Real cause: {}", name, message, e);
            return false;
        } finally {
            checked = true;
        }
    }
}
