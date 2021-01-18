package org.apache.camel.component.stitch.client;

import java.util.Map;

import org.apache.camel.component.stitch.client.models.StitchRequestBody;
import org.apache.camel.component.stitch.client.models.StitchResponse;
import reactor.core.publisher.Mono;

public interface StitchClient {
    /**
     * Create a batch
     *
     * Resource URL: /v2/import/batch
     *
     * Pushes a record or multiple records for a specified table to Stitch. Each request to this endpoint may only
     * contain data for a single table. When data for a table is pushed for the first time, Stitch will create the table
     * in the destination in the specified integration schema.
     *
     * How data is loaded during subsequent pushes depends on: 1. The loading behavior types used by the destination.
     * Stitch supports Upsert and Append-Only loading. 2. Whether the key_names property specifies Primary Key fields.
     * If Primary Keys aren’t specified, data will be loaded using Append-Only loading.
     *
     * @param requestBody the required arguments as StitchRequestBody
     */
    Mono<StitchResponse> batch(StitchRequestBody requestBody);

    Mono<StitchResponse> batch(Map<String, Object> requestBody);
}
