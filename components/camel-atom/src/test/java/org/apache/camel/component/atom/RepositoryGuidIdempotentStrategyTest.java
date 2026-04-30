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
package org.apache.camel.component.atom;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import com.apptasticsoftware.rssreader.Item;
import com.apptasticsoftware.rssreader.RssReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RepositoryGuidIdempotentStrategyTest {

    @Test
    void testIsValidItemReturnsTrueForNullEntry() {
        RepositoryGuidIdempotentStrategy strategy = new RepositoryGuidIdempotentStrategy();

        assertTrue(strategy.isValidItem(null));
    }

    @Test
    void testIsValidItemAcceptsFirstGuidAndRejectsDuplicate() throws Exception {
        RepositoryGuidIdempotentStrategy strategy = new RepositoryGuidIdempotentStrategy();
        Item item = loadItemsWithGuid(1).get(0);

        assertTrue(strategy.isValidItem(item), "First time GUID should be accepted");
        assertFalse(strategy.isValidItem(item), "Duplicate GUID should be rejected");
    }

    @Test
    void testIsValidItemAcceptsDifferentGuids() throws Exception {
        RepositoryGuidIdempotentStrategy strategy = new RepositoryGuidIdempotentStrategy();
        List<Item> items = loadItemsWithGuid(2);

        assertTrue(strategy.isValidItem(items.get(0)), "First GUID should be accepted");
        assertTrue(strategy.isValidItem(items.get(1)), "Second, different GUID should also be accepted");
    }

    private static List<Item> loadItemsWithGuid(int count) throws Exception {
        try (InputStream is = Files.newInputStream(Path.of("src/test/data/feed.atom"))) {
            return new RssReader()
                    .read(is)
                    .filter(item -> item.getGuid().isPresent())
                    .limit(count)
                    .collect(Collectors.toList());
        }
    }
}
