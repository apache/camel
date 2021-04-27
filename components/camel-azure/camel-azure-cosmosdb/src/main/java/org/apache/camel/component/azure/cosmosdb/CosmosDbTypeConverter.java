package org.apache.camel.component.azure.cosmosdb;

import com.azure.cosmos.models.PartitionKey;
import org.apache.camel.Converter;
import org.apache.camel.util.ObjectHelper;

@Converter(generateLoader = true)
public final class CosmosDbTypeConverter {

    private CosmosDbTypeConverter() {
    }

    @Converter
    public static PartitionKey toPartitionKey(final String partitionKeyAsString) {
        if (ObjectHelper.isNotEmpty(partitionKeyAsString)) {
            return new PartitionKey(partitionKeyAsString);
        }
        return null;
    }
}
