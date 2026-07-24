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

import org.apache.camel.health.HealthCheck;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.ListQueryExecutionsRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AwsServiceException#awsErrorDetails()} is nullable, so the health check must not dereference it blindly when
 * recording the AWS error code.
 */
class Athena2ProducerHealthCheckErrorDetailsTest {

    @Test
    void reportsDownWithoutFailingWhenTheExceptionCarriesNoErrorDetails() {
        HealthCheck.Result result = callHealthCheckThrowing(
                AwsServiceException.builder().message("boom").statusCode(500).build());

        assertThat(result.getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(result.getMessage()).contains("boom");
        assertThat(result.getDetails()).containsEntry(AbstractHealthCheck.SERVICE_STATUS_CODE, 500);
        // there were no error details to report, so the detail is simply absent
        assertThat(result.getDetails()).doesNotContainKey(AbstractHealthCheck.SERVICE_ERROR_CODE);
    }

    @Test
    void stillReportsTheErrorCodeWhenTheExceptionCarriesErrorDetails() {
        HealthCheck.Result result = callHealthCheckThrowing(
                AwsServiceException.builder()
                        .message("boom")
                        .statusCode(400)
                        .awsErrorDetails(AwsErrorDetails.builder()
                                .errorCode("InvalidRequestException")
                                .build())
                        .build());

        assertThat(result.getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(result.getDetails())
                .containsEntry(AbstractHealthCheck.SERVICE_ERROR_CODE, "InvalidRequestException");
    }

    private HealthCheck.Result callHealthCheckThrowing(AwsServiceException exception) {
        AthenaClient client = mock(AthenaClient.class);
        when(client.listQueryExecutions(any(ListQueryExecutionsRequest.class))).thenThrow(exception);

        Athena2Endpoint endpoint = mock(Athena2Endpoint.class);
        // a null region skips the region check and takes the health check straight to the client call
        when(endpoint.getConfiguration()).thenReturn(new Athena2Configuration());
        when(endpoint.getAthenaClient()).thenReturn(client);

        return new Athena2ProducerHealthCheck(endpoint, "test").call();
    }
}
