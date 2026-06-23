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

import java.text.ParseException;
import java.util.Date;
import java.util.Optional;

import com.apptasticsoftware.rssreader.Item;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class ItemUpdatedIdempotentStrategyTest {

    /**
     * This class tests the isValidItem method in the ItemUpdatedIdempotentStrategy class. The method determines whether
     * an RSS item is valid based on its updated or published date in relation to the previously processed item.
     */

    @Test
    void testValidEntryWithNewerUpdatedDate() throws ParseException {
        // Arrange
        ItemUpdatedIdempotentStrategy strategy = new ItemUpdatedIdempotentStrategy();
        Item item = Mockito.mock(Item.class);

        Date olderUpdate = AtomConverter.toDate("2023-01-01T00:00:00Z");
        Date newerUpdate = AtomConverter.toDate("2023-02-01T00:00:00Z");
        when(item.getUpdated()).thenReturn(Optional.of("2023-02-01T00:00:00Z"));

        // Act and Assert
        assertTrue(strategy.isValidItem(item));
        when(item.getUpdated()).thenReturn(Optional.of("2023-01-01T00:00:00Z"));
        assertFalse(strategy.isValidItem(item));
    }

    @Test
    void testValidEntryWithPubDateFallback() throws ParseException {
        // Arrange
        ItemUpdatedIdempotentStrategy strategy = new ItemUpdatedIdempotentStrategy();
        Item item = Mockito.mock(Item.class);

        when(item.getUpdated()).thenReturn(Optional.empty());
        when(item.getPubDate()).thenReturn(Optional.of("2023-03-01T00:00:00Z"));

        // Act and Assert
        assertTrue(strategy.isValidItem(item));

        when(item.getPubDate()).thenReturn(Optional.of("2023-01-01T00:00:00Z"));
        assertFalse(strategy.isValidItem(item));
    }

    @Test
    void testValidEntryWithNoDatesAvailable() {
        // Arrange
        Item item = Mockito.mock(Item.class);
        when(item.getUpdated()).thenReturn(Optional.empty());
        when(item.getPubDate()).thenReturn(Optional.empty());

        ItemUpdatedIdempotentStrategy strategy = new ItemUpdatedIdempotentStrategy();

        // Act and Assert
        assertTrue(strategy.isValidItem(item));
    }

    @Test
    void testEntryWithInvalidDateString() {
        // Arrange
        Item item = Mockito.mock(Item.class);
        when(item.getUpdated()).thenReturn(Optional.of("invalid-date"));
        when(item.getPubDate()).thenReturn(Optional.of("invalid-date"));

        ItemUpdatedIdempotentStrategy strategy = new ItemUpdatedIdempotentStrategy();

        // Act
        boolean isValid = strategy.isValidItem(item);

        // Assert
        assertTrue(isValid);
    }

    @Test
    void testEntryWithEqualDate() throws ParseException {
        // Arrange
        Item item1 = Mockito.mock(Item.class);
        Item item2 = Mockito.mock(Item.class);
        ItemUpdatedIdempotentStrategy strategy = new ItemUpdatedIdempotentStrategy();

        Date date = AtomConverter.toDate("2023-01-01T00:00:00Z");
        when(item1.getUpdated()).thenReturn(Optional.of("2023-01-01T00:00:00Z"));
        when(item2.getUpdated()).thenReturn(Optional.of("2023-01-01T00:00:00Z"));

        // Act and Assert
        // First item is valid
        assertTrue(strategy.isValidItem(item1));

        // Second item is invalid because it has the same updated date
        assertFalse(strategy.isValidItem(item2));
    }
}
