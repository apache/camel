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

import java.nio.file.Paths;

import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SftpUtil {
    private static final Logger LOG = LoggerFactory.getLogger(SftpUtil.class);

    private static boolean checked;
    private static boolean hasRequiredAlgorithms;

    private SftpUtil() {

    }

    @Deprecated
    public static boolean hasRequiredAlgorithms() {
        return hasRequiredAlgorithms("src/test/resources/hostkey.pem");
    }

    public static boolean hasRequiredAlgorithms(String keyPairFile) {
        if (!checked) {
            hasRequiredAlgorithms = doCheck(keyPairFile);
        }

        return hasRequiredAlgorithms;
    }

    private static boolean doCheck(String keyPairFile) {
        try {
            FileKeyPairProvider provider = new FileKeyPairProvider(Paths.get(keyPairFile));

            provider.loadKeys(null);
            return true;
        } catch (Exception e) {
            String name = System.getProperty("os.name");
            String message = e.getMessage();

            LOG.warn("SunX509 is not available on this platform [{}] Testing is skipped! Real cause: {}", name, message, e);
            return false;
        } finally {
            checked = true;
        }
    }
}
