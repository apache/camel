package org.apache.camel.component.azure.storage.blob;

public enum BlobOperations {
    /**
     * Returns a list of containers in the storage account.
     */
    listBlobContainers,
    /**
     * Creates a new container within a storage account. If a container with the same name already exists,
     * the producer will ignore it.
     */
    createBlobContainer,
    /**
     * Deletes the specified container in the storage account. If the container doesn't exist the operation fails.
     */
    deleteBlobContainer,
    /**
     * Returns a list of blobs in this container, with folder structures flattened.
     */
    listBlobs,
    /**
     * Get the content of the blob, can be restricted to a blob range.
     */
    getBlob,
    /**
     * Delete a blob
     */
    deleteBlob
}
