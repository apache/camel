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
package org.apache.camel.pgevent;

import java.sql.Connection;

import com.impossibl.postgres.api.jdbc.PGConnection;
import org.apache.camel.component.pgevent.PgEventHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PgEventHelperTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testToPGConnectionWithNullConnection() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        PgEventHelper.toPGConnection(null);
    }

    @Test
    public void testToPGConnectionWithNonWrappedConnection() throws Exception {
        Connection originalConnection = mock(PGConnection.class);
        PGConnection actualConnection = PgEventHelper.toPGConnection(originalConnection);
        assertSame(originalConnection, actualConnection);
    }

    @Test
    public void testToPGConnectionWithWrappedConnection() throws Exception {
        Connection wrapperConnection = mock(Connection.class);
        PGConnection unwrappedConnection = mock(PGConnection.class);
        when(wrapperConnection.isWrapperFor(PGConnection.class)).thenReturn(true);
        when(wrapperConnection.unwrap(PGConnection.class)).thenReturn(unwrappedConnection);
        PGConnection actualConnection = PgEventHelper.toPGConnection(wrapperConnection);
        assertSame(unwrappedConnection, actualConnection);
    }

    @Test
    public void testToPGConnectionWithInvalidWrappedConnection() throws Exception {
        expectedException.expect(IllegalStateException.class);
        Connection wrapperConnection = mock(Connection.class);
        when(wrapperConnection.isWrapperFor(PGConnection.class)).thenReturn(false);
        PgEventHelper.toPGConnection(wrapperConnection);
    }
}
