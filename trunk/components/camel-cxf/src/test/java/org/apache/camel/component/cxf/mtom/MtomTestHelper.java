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
package org.apache.camel.component.cxf.mtom;

import java.io.IOException;

import org.apache.cxf.helpers.IOUtils;

import org.junit.Assert;

/**
 * Package local test helper
 * 
 * @version 
 */
public final class MtomTestHelper {
    
    static final String SERVICE_TYPES_NS = "http://apache.org/camel/cxf/mtom_feature/types";
    static final String XOP_NS = "http://www.w3.org/2004/08/xop/include";
    static final byte[] REQ_PHOTO_DATA = {1, 0, -1, 34, 23, 3, 23, 55, 33};
    static final byte[] RESP_PHOTO_DATA = {5, -7, 23, 1, 0, -1, 34, 23, 3, 23, 55, 33, 3};

    static final String REQ_PHOTO_CID = "e33b6792-6666-4837-b0d9-68c88c8cadcb-1@apache.org";
    static final String REQ_IMAGE_CID = "e33b6792-6666-4837-b0d9-68c88c8cadcb-2@apache.org";
    
    static final String REQ_MESSAGE = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
        + "<Detail xmlns=\"http://apache.org/camel/cxf/mtom_feature/types\">"
        + "<photo><xop:Include xmlns:xop=\"http://www.w3.org/2004/08/xop/include\""
        + " href=\"cid:" + REQ_PHOTO_CID + "\"/>"
        + "</photo><image><xop:Include xmlns:xop=\"http://www.w3.org/2004/08/xop/include\""
        + " href=\"cid:" + REQ_IMAGE_CID + "\"/></image></Detail>";

    static final String MTOM_DISABLED_REQ_MESSAGE = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
        + "<Detail xmlns=\"http://apache.org/camel/cxf/mtom_feature/types\">"
        + "<photo>cid:" + REQ_PHOTO_CID + "</photo>"
        + "<image>cid:" + REQ_IMAGE_CID + "</image></Detail>";
    
    static final String RESP_PHOTO_CID = "4c7b78dc-356a-4fca-8877-068ee2f31824-7@apache.org";
    static final String RESP_IMAGE_CID = "4c7b78dc-356a-4fca-8877-068ee2f31824-8@apache.org";
    
    static final String RESP_MESSAGE = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
        + "<DetailResponse xmlns=\"http://apache.org/camel/cxf/mtom_feature/types\">"
        + "<photo><xop:Include xmlns:xop=\"http://www.w3.org/2004/08/xop/include\""
        + " href=\"cid:" + RESP_PHOTO_CID + "\"/>"
        + "</photo><image><xop:Include xmlns:xop=\"http://www.w3.org/2004/08/xop/include\""
        + " href=\"cid:" + RESP_IMAGE_CID + "\"/></image></DetailResponse>";

    static final String MTOM_DISABLED_RESP_MESSAGE = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
        + "<DetailResponse xmlns=\"http://apache.org/camel/cxf/mtom_feature/types\">"
        + "<photo>cid:" + RESP_PHOTO_CID + "</photo>"
        + "<image>cid:" + RESP_IMAGE_CID + "</image></DetailResponse>";

    static byte[] requestJpeg;
    static byte[] responseJpeg;

    static { 
        try {
            requestJpeg = IOUtils.readBytesFromStream(CxfMtomConsumerPayloadModeTest.class.getResourceAsStream("/java.jpg"));
            responseJpeg = IOUtils.readBytesFromStream(CxfMtomConsumerPayloadModeTest.class.getResourceAsStream("/Splash.jpg"));
        } catch (IOException e) {
            
            e.printStackTrace();
        }
    }
    
    private MtomTestHelper() {
        // utility class
    }

    static void assertEquals(byte[] bytes1, byte[] bytes2) {
        Assert.assertNotNull(bytes1);
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(bytes1.length, bytes2.length);
        for (int i = 0; i < bytes1.length; i++) {
            Assert.assertEquals(bytes1[i], bytes2[i]);
        }
    }

    static boolean isAwtHeadless(org.apache.commons.logging.Log log, org.slf4j.Logger logger) {
        Assert.assertFalse("Both loggers are not allowed to be null!", log == null && logger == null);
        boolean headless = Boolean.getBoolean("java.awt.headless");
        if (headless) {
            // having the conversion characters %c{1} inside log4j.properties will reveal us the
            // test class currently running as we make use of it's logger to warn about skipping!
            String warning = "Running headless. Skipping test as Images may not work.";
            if (log != null) {
                log.warn(warning);
            }

            if (logger != null) {
                logger.warn("Running headless. Skipping test as Images may not work.");
            }
        }

        return headless;
    }
}
