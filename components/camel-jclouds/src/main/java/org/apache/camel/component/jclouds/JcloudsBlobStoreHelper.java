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

package org.apache.camel.component.jclouds;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import org.apache.camel.util.IOHelper;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.domain.Blob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JcloudsBlobStoreHelper {

    private static final Logger LOG = LoggerFactory.getLogger(JcloudsBlobStoreHelper.class);

    private JcloudsBlobStoreHelper() {
        //Utility Class
    }

    /**
     * Writes payload to the the blobstore.
     *
     * @param blobStore
     * @param container
     * @param blobName
     * @param payload
     */
    public static void writeBlob(BlobStore blobStore, String container, String blobName, Object payload) {
        if (blobName != null && payload != null) {
            Blob blob = blobStore.blobBuilder(blobName).build();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = null;
            try {
                oos = new ObjectOutputStream(baos);
                oos.writeObject(payload);
                blob.setPayload(baos.toByteArray());
                blobStore.putBlob(container, blob);
            } catch (IOException e) {
                LOG.error("Error while writing blob", e);
            } finally {
                IOHelper.close(oos, baos);
            }
        }

    }

    /**
     * Reads from a {@link BlobStore}. It returns an Object.
     *
     * @param container
     * @param blobName
     * @return
     */
    public static Object readBlob(BlobStore blobStore, String container, String blobName, final ClassLoader classLoader) {
        Object result = null;
        ObjectInputStream ois = null;
        blobStore.createContainerInLocation(null, container);

        InputStream is = blobStore.getBlob(container, blobName).getPayload().getInput();

        try {
            ois = new ObjectInputStream(is) {
                @Override
                public Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                    try {
                        return classLoader.loadClass(desc.getName());
                    } catch (Exception e) {
                    }
                    return super.resolveClass(desc);
                }
            };
            result = ois.readObject();
        } catch (IOException
                e) {
            e.printStackTrace();
        } catch (ClassNotFoundException
                e) {
            e.printStackTrace();
        } finally {
            IOHelper.close(ois, is);
        }
        return result;
    }
}
