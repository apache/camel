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
package org.apache.camel.component.huaweicloud.obs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.obs.services.model.ObsObject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.huaweicloud.obs.constants.OBSConstants;
import org.apache.camel.component.huaweicloud.obs.constants.OBSHeaders;
import org.apache.camel.util.IOHelper;

public final class OBSUtils {
    private OBSUtils() {
    }

    /**
     * Convert input stream to a byte array
     *
     * @param  stream
     * @return
     * @throws IOException
     */
    public static byte[] toBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOHelper.copy(IOHelper.buffered(stream), bos);
        return bos.toByteArray();
    }

    /**
     * maps the Obs object along with all its meta-data into exchange
     *
     * @param exchange
     * @param obsObject
     */
    public static void mapObsObject(Exchange exchange, ObsObject obsObject) {
        Message message = exchange.getIn();

        // set exchange body to a byte array of object contents
        try {
            message.setBody(OBSUtils.toBytes(obsObject.getObjectContent()));
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        }

        // set all the message headers
        message.setHeader(OBSHeaders.BUCKET_NAME, obsObject.getBucketName());
        message.setHeader(OBSHeaders.OBJECT_KEY, obsObject.getObjectKey());
        message.setHeader(OBSHeaders.LAST_MODIFIED, obsObject.getMetadata().getLastModified());
        message.setHeader(OBSHeaders.CONTENT_LENGTH, obsObject.getMetadata().getContentLength());
        message.setHeader(OBSHeaders.CONTENT_TYPE, obsObject.getMetadata().getContentType());
        message.setHeader(OBSHeaders.ETAG, obsObject.getMetadata().getEtag());
        message.setHeader(OBSHeaders.CONTENT_MD5, obsObject.getMetadata().getContentMd5());

        message.setHeader(OBSHeaders.FILE_NAME, obsObject.getObjectKey());

        if (obsObject.getObjectKey().endsWith("/")) {
            message.setHeader(OBSHeaders.OBJECT_TYPE, OBSConstants.FOLDER);
        } else {
            message.setHeader(OBSHeaders.OBJECT_TYPE, OBSConstants.FILE);
        }
    }
}
