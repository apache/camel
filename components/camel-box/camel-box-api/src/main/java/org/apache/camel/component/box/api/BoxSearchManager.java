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
package org.apache.camel.component.box.api;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxItem;
import com.box.sdk.BoxSearch;
import com.box.sdk.BoxSearchParameters;
import com.box.sdk.PartialCollection;
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
    //200 is maximal value used for search (see javadoc for BoxSearch.searchRange)
    private static final int SEARCH_MAX_LIMIT = 200;

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

            // New box API for search requires offset and limit as parameters.
            // To preserve api from previous functionality fro previous version, we will execute more searches if needed and merge results
            BoxSearchParameters bsp = new BoxSearchParameters();
            bsp.setAncestorFolderIds(Collections.singletonList(folderId));
            bsp.setQuery(query);

            LinkedList<BoxItem> result = new LinkedList();
            BoxSearch bs = new BoxSearch(boxConnection);
            PartialCollection<BoxItem.Info> partialResult;
            int offset = 0;
            do {
                partialResult = bs.searchRange(offset, SEARCH_MAX_LIMIT, bsp);
                offset += partialResult.size();
                partialResult.stream().map(i -> (BoxItem)i.getResource()).forEachOrdered(result::add);
            } while(partialResult.size() == partialResult.limit());

            return result;
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }
}
