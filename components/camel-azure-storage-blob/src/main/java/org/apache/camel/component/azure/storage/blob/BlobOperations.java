package org.apache.camel.component.azure.storage.blob;

public enum BlobOperations {
    /**
     * Returns a list of containers in the storage account.
     */
    listBlobContainers,
    /**
     * Returns a list of blobs in this container, with folder structures flattened.
     */
    listBlobs
}
