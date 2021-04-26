package org.apache.camel.component.azure.cosmosdb;

public enum CosmosDbOperationsDefinition {
    // Operations on the service level (direct from the client)
    listDatabases,
    createDatabase,
    queryDatabases,

    // Operations on the database level (database name is necessary here)
    deleteDatabase,
    createContainer,
    replaceDatabaseThroughput,
    listContainers,
    queryContainers,

    // Operations on the container level (database name and container name are necessary here)
    deleteContainer,
    replaceContainerThroughput,
    createItem,
    upsertItem,
    deleteItem,
    readItem,
    readAllItems,
    queryItem
}
