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
package org.apache.camel.component.olingo4;

import java.util.List;

import org.apache.olingo.client.api.domain.ClientCollectionValue;
import org.apache.olingo.client.api.domain.ClientComplexValue;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.domain.ClientObjectFactory;
import org.apache.olingo.client.api.domain.ClientValue;
import org.apache.olingo.client.core.domain.ClientObjectFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Olingo4IndexTest {

    private final ClientObjectFactory factory = new ClientObjectFactoryImpl();

    private ClientComplexValue location(String city) {
        ClientComplexValue value = factory.newComplexValue("Microsoft.OData.SampleService.Models.TripPin.AirportLocation");
        value.add(factory.newPrimitiveProperty("Address", factory.newPrimitiveValueBuilder().buildString("Main Street")));
        value.add(factory.newPrimitiveProperty("City", factory.newPrimitiveValueBuilder().buildString(city)));
        return value;
    }

    private ClientCollectionValue<ClientValue> emails(String... emails) {
        ClientCollectionValue<ClientValue> value = factory.newCollectionValue("Edm.String");
        for (String email : emails) {
            value.add(factory.newPrimitiveValueBuilder().buildString(email));
        }
        return value;
    }

    @Test
    public void testComplexValueIsOneResult() {
        Olingo4Index index = new Olingo4Index();

        Object first = index.filterResponse(location("Boise"));
        ClientComplexValue value = assertInstanceOf(ClientComplexValue.class, first);
        assertEquals("Boise", value.get("City").getPrimitiveValue().toString());
        index.index(first);

        // the same value read again was seen before
        assertNull(index.filterResponse(location("Boise")));

        // a changed value is a new result
        Object changed = index.filterResponse(location("Seattle"));
        value = assertInstanceOf(ClientComplexValue.class, changed);
        assertEquals("Seattle", value.get("City").getPrimitiveValue().toString());
    }

    @Test
    public void testCollectionValueKeepsItsType() {
        Olingo4Index index = new Olingo4Index();

        Object first = index.filterResponse(emails("a@example.com", "b@example.com"));
        ClientCollectionValue<?> value = assertInstanceOf(ClientCollectionValue.class, first);
        assertEquals("Collection(Edm.String)", value.getTypeName());
        assertEquals(2, value.size());
        index.index(first);

        // nothing new: an empty collection value, not a List
        Object second = index.filterResponse(emails("a@example.com", "b@example.com"));
        value = assertInstanceOf(ClientCollectionValue.class, second);
        assertTrue(value.isEmpty());

        // only the value not read before
        Object third = index.filterResponse(emails("a@example.com", "b@example.com", "c@example.com"));
        value = assertInstanceOf(ClientCollectionValue.class, third);
        assertEquals("Collection(Edm.String)", value.getTypeName());
        assertEquals(1, value.size());
        assertEquals("c@example.com", value.iterator().next().asPrimitive().toString());
    }

    @Test
    public void testEntitySet() {
        Olingo4Index index = new Olingo4Index();

        ClientEntitySet entitySet = factory.newEntitySet();
        entitySet.getEntities().add(factory.newEntity(null));
        Object first = index.filterResponse(entitySet);
        assertSame(entitySet, first);
        assertEquals(1, entitySet.getEntities().size());
        index.index(first);

        ClientEntitySet again = factory.newEntitySet();
        again.getEntities().add(factory.newEntity(null));
        Object second = index.filterResponse(again);
        assertSame(again, second);
        assertTrue(again.getEntities().isEmpty());
    }

    @Test
    public void testListIsFilteredPerElement() {
        Olingo4Index index = new Olingo4Index();

        Object first = index.filterResponse(List.of("a", "b"));
        assertEquals(List.of("a", "b"), first);
        index.index(first);

        assertEquals(List.of("c"), index.filterResponse(List.of("a", "b", "c")));
        assertEquals(List.of("c"), index.filterResponse(new String[] { "a", "c" }));
    }
}
