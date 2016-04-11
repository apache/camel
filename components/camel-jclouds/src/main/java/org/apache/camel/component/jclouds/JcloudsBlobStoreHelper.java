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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.ws.rs.core.MediaType;

import com.google.common.base.Strings;

import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.util.BlobStoreUtils;
import org.jclouds.domain.Location;
import org.jclouds.io.Payload;

import static org.jclouds.blobstore.options.PutOptions.Builder.multipart;

public final class JcloudsBlobStoreHelper {

    private JcloudsBlobStoreHelper() {
        //Utility Class
    }

    /**
     * Creates all directories that are part of the blobName.
     */
    public static void mkDirs(BlobStore blobStore, String container, String blobName) {
        if (blobStore != null && !Strings.isNullOrEmpty(blobName) && blobName.contains("/")) {
            String directory = BlobStoreUtils.parseDirectoryFromPath(blobName);
            blobStore.createDirectory(container, directory);
        }
    }

    /**
     * Checks if container exists and creates one if not.
     *
     * @param blobStore  The {@link BlobStore} to use.
     * @param container  The container name to check against.
     * @param locationId The locationId to create the container if not found.
     */
    public static void ensureContainerExists(BlobStore blobStore, String container, String locationId) {
        if (blobStore != null && !Strings.isNullOrEmpty(container) && !blobStore.containerExists(container)) {
            blobStore.createContainerInLocation(getLocationById(blobStore, locationId), container);
        }
    }

    /**
     * Returns the {@link Location} that matches the locationId.
     */
    public static Location getLocationById(BlobStore blobStore, String locationId) {
        if (blobStore != null && !Strings.isNullOrEmpty(locationId)) {
            for (Location location : blobStore.listAssignableLocations()) {
                if (locationId.equals(location.getId())) {
                    return location;
                }
            }
        }
        return null;
    }

    /**
     * Writes {@link Payload} to the the {@link BlobStore}.
     */
    public static void writeBlob(BlobStore blobStore, String container, String blobName, Payload payload) {
        if (blobName != null && payload != null) {
            mkDirs(blobStore, container, blobName);
            Blob blob = blobStore.blobBuilder(blobName).payload(payload).contentType(MediaType.APPLICATION_OCTET_STREAM).contentDisposition(blobName).build();
            blobStore.putBlob(container, blob, multipart());
        }
    }

    /**
     * Reads from a {@link BlobStore}. It returns an Object.
     */
    public static InputStream readBlob(BlobStore blobStore, String container, String blobName) throws IOException {
        InputStream is = null;
        if (!Strings.isNullOrEmpty(blobName)) {
            Blob blob = blobStore.getBlob(container, blobName);
            if (blob != null && blob.getPayload() != null) {
                is = blobStore.getBlob(container, blobName).getPayload().openStream();
            }
        }
        return is;
    }
    
    /**
     * Return the count of all the blobs in the container
     */
    public static long countBlob(BlobStore blobStore, String container) {
        long blobsCount = blobStore.countBlobs(container);
        return blobsCount;
    }
    

    /**
     * Remove a specific blob from a {@link BlobStore}
     */
    public static void removeBlob(BlobStore blobStore, String container, String blobName) throws IOException {
        if (!Strings.isNullOrEmpty(blobName)) {
            blobStore.removeBlob(container, blobName);            
        }
    }
    
    /**
     * Clear a {@link BlobStore} specific container
     */
    public static void clearContainer(BlobStore blobStore, String container) throws IOException {
        blobStore.clearContainer(container);           
    }
    
    /**
     * Delete a {@link BlobStore} specific container
     */
    public static void deleteContainer(BlobStore blobStore, String container) throws IOException {
        blobStore.deleteContainer(container);
    }
    
    /**
     * Check if a {@link BlobStore} specific container exists or not
     */
    public static boolean containerExists(BlobStore blobStore, String container) throws IOException {
        boolean result = blobStore.containerExists(container);
        return result;
    }
    
    /**
     * Delete a list of {@link BlobStore} blob
     */
    public static void removeBlobs(BlobStore blobStore, String container, List blobNames) throws IOException {
        blobStore.removeBlobs(container, blobNames);
    }
}
