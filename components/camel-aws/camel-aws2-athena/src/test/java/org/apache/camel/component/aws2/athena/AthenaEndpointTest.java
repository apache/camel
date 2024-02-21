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
package org.apache.camel.component.aws2.athena;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.EncryptionOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
public class AthenaEndpointTest {

    @Mock
    private AthenaClient amazonAthenaClient;

    private CamelContext camelContext;

    @BeforeEach
    public void setup() {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("athenaClient", amazonAthenaClient);
        camelContext = new DefaultCamelContext(registry);
    }

    @Test
    public void allTheEndpointParams() {
        Athena2Configuration configuration = ((Athena2Endpoint) camelContext.getEndpoint(
                "aws2-athena://label"
                                                                                         + "?amazonAthenaClient=#athenaClient"
                                                                                         + "&operation=startQueryExecution"
                                                                                         + "&database=db"
                                                                                         + "&queryExecutionId=id"
                                                                                         + "&workGroup=wg"
                                                                                         + "&nextToken=nt"
                                                                                         + "&maxResults=42"
                                                                                         + "&includeTrace=true"
                                                                                         + "&outputLocation=bucket"
                                                                                         + "&outputType=SelectList"
                                                                                         + "&clientRequestToken=crt"
                                                                                         + "&queryString=select"
                                                                                         + "&encryptionOption=SSE_S3"
                                                                                         + "&kmsKey=key"
                                                                                         + "&waitTimeout=1"
                                                                                         + "&initialDelay=2"
                                                                                         + "&delay=3"
                                                                                         + "&maxAttempts=4"
                                                                                         + "&retry=always"
                                                                                         + "&accessKey=unused"
                                                                                         + "&secretKey=unused"
                                                                                         + "&resetWaitTimeoutOnRetry=false"))
                .getConfiguration();

        assertEquals(amazonAthenaClient, configuration.getAmazonAthenaClient());
        assertEquals(Athena2Operations.startQueryExecution, configuration.getOperation());
        assertEquals("db", configuration.getDatabase());
        assertEquals("id", configuration.getQueryExecutionId());
        assertEquals("wg", configuration.getWorkGroup());
        assertEquals("nt", configuration.getNextToken());
        assertEquals(42, configuration.getMaxResults());
        assertEquals(true, configuration.isIncludeTrace());
        assertEquals("bucket", configuration.getOutputLocation());
        assertEquals(Athena2OutputType.SelectList, configuration.getOutputType());
        assertEquals("crt", configuration.getClientRequestToken());
        assertEquals("select", configuration.getQueryString());
        assertEquals(EncryptionOption.SSE_S3, configuration.getEncryptionOption());
        assertEquals("key", configuration.getKmsKey());
        assertEquals(1, configuration.getWaitTimeout());
        assertEquals(2, configuration.getInitialDelay());
        assertEquals(3, configuration.getDelay());
        assertEquals(4, configuration.getMaxAttempts());
        assertEquals("always", configuration.getRetry());
        assertEquals(false, configuration.isResetWaitTimeoutOnRetry());
    }

    @Test
    public void defaultEndpointParams() {
        Athena2Configuration configuration = ((Athena2Endpoint) camelContext.getEndpoint(
                "aws2-athena://label?accessKey=unused&secretKey=unused")).getConfiguration();

        assertEquals(amazonAthenaClient, configuration.getAmazonAthenaClient());
        assertEquals(Athena2Operations.startQueryExecution, configuration.getOperation());
        assertNull(configuration.getDatabase());
        assertNull(configuration.getQueryExecutionId());
        assertNull(configuration.getWorkGroup());
        assertNull(configuration.getNextToken());
        assertNull(configuration.getMaxResults());
        assertEquals(false, configuration.isIncludeTrace());
        assertNull(configuration.getOutputLocation());
        assertEquals(Athena2OutputType.StreamList, configuration.getOutputType());
        assertNull(configuration.getClientRequestToken());
        assertNull(configuration.getQueryString());
        assertNull(configuration.getEncryptionOption());
        assertNull(configuration.getKmsKey());
        assertEquals(0, configuration.getWaitTimeout());
        assertEquals(1_000, configuration.getInitialDelay());
        assertEquals(2_000, configuration.getDelay());
        assertEquals(1, configuration.getMaxAttempts());
        assertNull(configuration.getRetry());
        assertEquals(true, configuration.isResetWaitTimeoutOnRetry());
    }

    @Test
    public void getQueryExecutionOperationParams() {
        Athena2Configuration configuration = ((Athena2Endpoint) camelContext.getEndpoint(
                "aws2-athena://label"
                                                                                         + "?operation=getQueryExecution"
                                                                                         + "&accessKey=unused"
                                                                                         + "&secretKey=unused"
                                                                                         + "&queryExecutionId=123"))
                .getConfiguration();

        assertEquals(Athena2Operations.getQueryExecution, configuration.getOperation());
        assertEquals("123", configuration.getQueryExecutionId());
    }

    @Test
    public void getQueryResultsOperationParams() {
        Athena2Configuration configuration = ((Athena2Endpoint) camelContext.getEndpoint(
                "aws2-athena://label"
                                                                                         + "?operation=getQueryResults"
                                                                                         + "&queryExecutionId=123"
                                                                                         + "&outputType=SelectList"
                                                                                         + "&maxResults=1"
                                                                                         + "&accessKey=unused"
                                                                                         + "&secretKey=unused"
                                                                                         + "&nextToken=nt"))
                .getConfiguration();

        assertEquals(Athena2Operations.getQueryResults, configuration.getOperation());
        assertEquals("123", configuration.getQueryExecutionId());
        assertEquals(Athena2OutputType.SelectList, configuration.getOutputType());
        assertEquals(1, configuration.getMaxResults());
        assertEquals("nt", configuration.getNextToken());
    }

    @Test
    public void listQueryExecutionsOperationParams() {
        Athena2Configuration configuration = ((Athena2Endpoint) camelContext.getEndpoint(
                "aws2-athena://label"
                                                                                         + "?operation=listQueryExecutions"
                                                                                         + "&maxResults=1"
                                                                                         + "&nextToken=nt"
                                                                                         + "&accessKey=unused"
                                                                                         + "&secretKey=unused"
                                                                                         + "&workGroup=wg"))
                .getConfiguration();

        assertEquals(Athena2Operations.listQueryExecutions, configuration.getOperation());
        assertEquals(1, configuration.getMaxResults());
        assertEquals("nt", configuration.getNextToken());
        assertEquals("wg", configuration.getWorkGroup());
    }

}
