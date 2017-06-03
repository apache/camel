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
package org.apache.camel.component.box.api;

import java.util.ArrayList;
import java.util.Collection;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Box Search Manager
 * 
 * <p>
 * Provides operations to manage Box searches.
 * 
 * 
 *
 */
public class BoxSearchManager {

    private static final Logger LOG = LoggerFactory.getLogger(BoxSearchManager.class);

    /**
     * Box connection to authenticated user account.
     */
    private BoxAPIConnection boxConnection;

    /**
     * Create search manager to manage the searches of Box connection's
     * authenticated user.
     * 
     * @param boxConnection
     *            - Box connection to authenticated user account.
     */
    public BoxSearchManager(BoxAPIConnection boxConnection) {
        this.boxConnection = boxConnection;
    }

    /**
     * Search folder and all descendant folders using the given query.
     * 
     * @param folderId
     *            - the id of folder searched.
     * @param query
     *            - the search query.
     * 
     * @return A collection of matching items.
     */
    public Collection<BoxItem> searchFolder(String folderId, String query) {
        try {
            LOG.debug("Searching folder(id=" + folderId + ") with query=" + query);

            if (folderId == null) {
                throw new IllegalArgumentException("Parameter 'folderId' can not be null");
            }
            if (query == null) {
                throw new IllegalArgumentException("Parameter 'query' can not be null");
            }

            BoxFolder folder = new BoxFolder(boxConnection, folderId);

            Collection<BoxItem> results = new ArrayList<BoxItem>();
            for (BoxItem.Info info : folder.search(query)) {
                results.add((BoxItem) info.getResource());
            }

            return results;
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }
}
