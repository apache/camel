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
package org.apache.camel.test.spring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.camel.test.spring.junit6.CamelSpringTestContextLoaderTestExecutionListener;
import org.apache.camel.test.spring.junit6.SpringTestExecutionListenerSorter;
import org.apache.camel.test.spring.junit6.StopWatchTestExecutionListener;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.spring.junit6.SpringTestExecutionListenerSorter.getPrecedence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SpringTestExecutionListenerSorterTest {

    @Test
    void getPrecedencesForRegisteredClassesShouldReturnCorrectOrder() {

        List<Class<?>> listenersInExpectedOrder = new ArrayList<>();
        listenersInExpectedOrder.add(CamelSpringTestContextLoaderTestExecutionListener.class);
        listenersInExpectedOrder.add(StopWatchTestExecutionListener.class);

        List<Class<?>> listenersSortedByPrecedence = new ArrayList<>(listenersInExpectedOrder);
        listenersSortedByPrecedence.sort(Comparator.comparingInt(SpringTestExecutionListenerSorter::getPrecedence));

        assertEquals(listenersInExpectedOrder, listenersSortedByPrecedence);
    }

    @Test
    void getPrecedenceForWrongClassShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> getPrecedence(Object.class));
    }

}
