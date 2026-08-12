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
package org.apache.camel.component.rest.postman.collection;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.rest.postman.model.PostmanCollection;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a {@code collectionSource} into a parsed {@link PostmanCollection}.
 * <p>
 * A source is either a resource URI, resolved through Camel's {@link ResourceHelper} so that {@code classpath:},
 * {@code file:} and {@code http:} all work, or a collection uid to be fetched from the Postman cloud.
 */
public final class PostmanCollectionLoader {

    public static final String SOURCE_TYPE_AUTO = "auto";
    public static final String SOURCE_TYPE_RESOURCE = "resource";
    public static final String SOURCE_TYPE_CLOUD = "cloud";

    private static final Logger LOG = LoggerFactory.getLogger(PostmanCollectionLoader.class);

    /**
     * A bare collection UUID, or the {@code {ownerId}-{uuid}} uid form the Postman API also accepts.
     */
    private static final Pattern UID_PATTERN = Pattern.compile(
            "(?:[0-9]+-)?[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static final String EXPECTED_SCHEMA_VERSION = "v2.1";

    private PostmanCollectionLoader() {
    }

    /**
     * Whether the source should be fetched from the Postman cloud rather than read as a resource.
     *
     * @param source     the collection source
     * @param sourceType {@code auto}, {@code resource} or {@code cloud}
     */
    public static boolean isCloudSource(String source, String sourceType) {
        if (SOURCE_TYPE_CLOUD.equals(sourceType)) {
            return true;
        }
        if (SOURCE_TYPE_RESOURCE.equals(sourceType)) {
            return false;
        }
        // auto: only a bare uid is treated as a cloud reference, so a file merely named after a uuid,
        // which would carry an extension or a scheme, still resolves as a resource
        return UID_PATTERN.matcher(source).matches();
    }

    /**
     * Loads a collection from a resource URI.
     */
    public static PostmanCollection loadFromResource(CamelContext camelContext, String uri) {
        try {
            Resource resource = ResourceHelper.resolveMandatoryResource(camelContext, uri);
            if (!resource.exists()) {
                throw new RuntimeCamelException("Postman collection not found: " + uri);
            }
            try (InputStream is = resource.getInputStream()) {
                if (is == null) {
                    throw new RuntimeCamelException("Postman collection not found: " + uri);
                }
                byte[] content = BoundedInputStreamReader.readAtMost(
                        is, PostmanCloudClient.MAX_COLLECTION_BYTES, "Postman collection " + uri);
                return parse(new String(content, StandardCharsets.UTF_8), uri);
            }
        } catch (RuntimeCamelException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeCamelException("Cannot load Postman collection from: " + uri, e);
        }
    }

    /**
     * Fetches a collection from the Postman cloud.
     */
    public static PostmanCollection loadFromCloud(PostmanCloudClient client, String uid) {
        try {
            return parse(client.fetchCollection(uid), uid);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeCamelException("Interrupted while fetching Postman collection: " + uid, e);
        } catch (RuntimeCamelException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeCamelException("Cannot fetch Postman collection from the Postman cloud: " + uid, e);
        }
    }

    /**
     * Parses a collection document and checks that it looks like the format we support.
     *
     * @param content the raw JSON
     * @param source  the source, used only for error messages
     */
    public static PostmanCollection parse(String content, String source) {
        JsonObject root;
        try {
            root = (JsonObject) Jsoner.deserialize(content);
        } catch (Exception e) {
            throw new RuntimeCamelException("Postman collection is not valid JSON: " + source, e);
        }
        if (root == null) {
            throw new RuntimeCamelException("Postman collection is empty: " + source);
        }

        PostmanCollection collection = PostmanCollection.parse(root);
        if (collection.getInfo() == null) {
            throw new RuntimeCamelException(
                    "Not a Postman collection, the info object is missing: " + source);
        }

        String schema = collection.getSchema();
        if (schema == null) {
            LOG.warn("Postman collection {} does not declare a schema. Assuming Collection Format {}.",
                    source, EXPECTED_SCHEMA_VERSION);
        } else if (!schema.contains(EXPECTED_SCHEMA_VERSION)) {
            LOG.warn("Postman collection {} declares schema {}, but only Collection Format {} is supported."
                     + " Parsing will continue and may fail or produce unexpected results.",
                    source, schema, EXPECTED_SCHEMA_VERSION);
        }
        return collection;
    }
}
