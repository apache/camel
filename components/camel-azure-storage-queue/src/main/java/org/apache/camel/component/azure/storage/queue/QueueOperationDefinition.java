package org.apache.camel.component.azure.storage.queue;

public enum QueueOperationDefinition {
    // Operations on the service level
    listQueues,
    // Operations on the queue level
    createQueue,
    deleteQueue,
    clearQueue,
    sendMessage,
    sendBatchMessages,
    deleteMessage,
    receiveMessages,
    peekMessages,
    updateMessage
}
