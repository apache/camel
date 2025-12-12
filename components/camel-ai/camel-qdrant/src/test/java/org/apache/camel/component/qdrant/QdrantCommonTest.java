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

package org.apache.camel.component.qdrant;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

final class QdrantCommonTest extends CamelTestSupport {

    @DisplayName("Tests that trying to run actions with a null body triggers a failure")
    @ParameterizedTest
    @EnumSource(QdrantAction.class)
    void upsertWithNullBody(QdrantAction action) {
        Exchange result = fluentTemplate.to("qdrant:test")
                .withHeader(QdrantHeaders.ACTION, action)
                .withBody(null)
                .request(Exchange.class);

        assertThat(result).isNotNull();

        if (action == QdrantAction.COLLECTION_INFO || action == QdrantAction.DELETE_COLLECTION) {
            // null body is OK for collection info and delete collection, but it throws an specific exception
            // if the collection doesn't exist
            assertThat(result.getException()).isInstanceOf(QdrantActionException.class);
        } else {
            assertThat(result.getException()).isInstanceOf(InvalidPayloadException.class);
            assertThat(result.getException().getMessage()).contains("No body available of type");
        }

    }
}
