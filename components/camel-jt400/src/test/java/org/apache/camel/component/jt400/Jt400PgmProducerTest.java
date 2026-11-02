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
package org.apache.camel.component.jt400;

import java.util.List;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.ProgramParameter;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Builds the program parameter list of {@link Jt400PgmProducer} without connecting to an IBM i system.
 */
public class Jt400PgmProducerTest extends Jt400TestSupport {

    private static final String PGM_URI = "jt400://USER:password@host/qsys.lib/library.lib/prog.pgm"
                                          + "?connectionPool=#mockPool&fieldsLength=4,8";

    private final AS400 iSeries = new AS400("host", "USER", "password");

    @Test
    public void testNoOutputFieldsIdxMeansInputOnly() throws Exception {
        Jt400Endpoint endpoint = context.getEndpoint(PGM_URI, Jt400Endpoint.class);
        assertNull(endpoint.getOutputFieldsIdxArray());
        assertFalse(endpoint.isFieldIdxForOuput(0));

        ProgramParameter[] parameters = createParameterList(endpoint, new Object[] { 1, 2L });

        assertEquals(2, parameters.length);
        assertArrayEquals(new byte[] { 0, 0, 0, 1 }, parameters[0].getInputData());
        assertEquals(0, parameters[0].getOutputDataLength());
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 0, 2 }, parameters[1].getInputData());
        assertEquals(0, parameters[1].getOutputDataLength());
    }

    @Test
    public void testListBody() throws Exception {
        Jt400Endpoint endpoint = context.getEndpoint(PGM_URI + "&outputFieldsIdx=1", Jt400Endpoint.class);

        ProgramParameter[] parameters = createParameterList(endpoint, List.of(1, new byte[0]));

        assertEquals(2, parameters.length);
        assertArrayEquals(new byte[] { 0, 0, 0, 1 }, parameters[0].getInputData());
        assertEquals(0, parameters[0].getOutputDataLength());
        assertEquals(8, parameters[1].getOutputDataLength());
    }

    @Test
    public void testInvalidBody() {
        Jt400Endpoint endpoint = context.getEndpoint(PGM_URI, Jt400Endpoint.class);

        assertThrows(InvalidPayloadException.class, () -> createParameterList(endpoint, new Object()));
    }

    private ProgramParameter[] createParameterList(Jt400Endpoint endpoint, Object body) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        return new Jt400PgmProducer(endpoint).getParameterList(exchange, iSeries);
    }

}
