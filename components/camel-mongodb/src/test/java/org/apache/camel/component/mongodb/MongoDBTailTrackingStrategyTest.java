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
package org.apache.camel.component.mongodb;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.bson.types.BSONTimestamp;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MongoDBTailTrackingStrategyTest {

    private static final String INCREASING_FIELD_NAME = "ts";

    @Test
    public void testExtractLastValForLiterals() throws Exception {
        int expected = 1483701465;
        DBObject o = mock(DBObject.class);
        when(o.get(INCREASING_FIELD_NAME)).thenReturn(expected);
        Object lastVal = MongoDBTailTrackingEnum.LITERAL.extractLastVal(o, INCREASING_FIELD_NAME);
        assertThat(lastVal, is(expected));
    }

    @Test
    public void testCreateQueryForLiterals() {
        Integer lastVal = 1483701465;
        BasicDBObject basicDBObject = MongoDBTailTrackingEnum.LITERAL.createQuery(lastVal, INCREASING_FIELD_NAME);
        final Object actual = basicDBObject.get(INCREASING_FIELD_NAME);
        assertThat(actual, is(notNullValue()));
        assertThat(actual instanceof BasicDBObject, is(true));
        assertThat(((BasicDBObject)actual).get("$gt"), is(lastVal));
    }

    @Test
    public void testExtractLastValForTimestamp() throws Exception {
        DBObject o = mock(DBObject.class);
        final int lastVal = 1483701465;
        when(o.get(INCREASING_FIELD_NAME)).thenReturn(new BSONTimestamp(lastVal, 1));
        Object res = MongoDBTailTrackingEnum.TIMESTAMP.extractLastVal(o, INCREASING_FIELD_NAME);
        assertThat(res, is(lastVal));
    }

    @Test
    public void testExtracCreateQueryForTimestamp() throws Exception {
        final int lastVal = 1483701465;
        BasicDBObject basicDBObject = MongoDBTailTrackingEnum.TIMESTAMP.createQuery(lastVal, INCREASING_FIELD_NAME);
        final Object actual = basicDBObject.get(INCREASING_FIELD_NAME);
        assertThat(actual, is(notNullValue()));
        assertThat(actual instanceof BasicDBObject, is(true));
        assertThat(((BasicDBObject)actual).get("$gt") instanceof BSONTimestamp, is(true));
        BSONTimestamp bsonTimestamp = (BSONTimestamp) ((BasicDBObject)actual).get("$gt");
        assertThat(bsonTimestamp.getTime(), is(lastVal));
    }


}