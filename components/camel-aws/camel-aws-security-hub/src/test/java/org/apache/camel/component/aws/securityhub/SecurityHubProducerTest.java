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
package org.apache.camel.component.aws.securityhub;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.securityhub.SecurityHubClient;
import software.amazon.awssdk.services.securityhub.model.AwsSecurityFinding;
import software.amazon.awssdk.services.securityhub.model.AwsSecurityFindingFilters;
import software.amazon.awssdk.services.securityhub.model.AwsSecurityFindingIdentifier;
import software.amazon.awssdk.services.securityhub.model.BatchImportFindingsRequest;
import software.amazon.awssdk.services.securityhub.model.BatchImportFindingsResponse;
import software.amazon.awssdk.services.securityhub.model.BatchUpdateFindingsRequest;
import software.amazon.awssdk.services.securityhub.model.BatchUpdateFindingsResponse;
import software.amazon.awssdk.services.securityhub.model.GetFindingsRequest;
import software.amazon.awssdk.services.securityhub.model.GetFindingsResponse;
import software.amazon.awssdk.services.securityhub.model.ImportFindingsError;
import software.amazon.awssdk.services.securityhub.model.StringFilter;
import software.amazon.awssdk.services.securityhub.model.StringFilterComparison;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SecurityHubProducerTest extends CamelTestSupport {

    @BindToRegistry("securityHubClient")
    private final SecurityHubClient securityHubClient = mock(SecurityHubClient.class);

    @Test
    public void getFindingsUsesFiltersOfTheRightType() {
        when(securityHubClient.getFindings(any(GetFindingsRequest.class)))
                .thenReturn(GetFindingsResponse.builder().build());
        AwsSecurityFindingFilters filters = AwsSecurityFindingFilters.builder()
                .severityLabel(StringFilter.builder().comparison(StringFilterComparison.EQUALS).value("HIGH").build())
                .build();

        Exchange exchange = template.send("direct:getFindings",
                e -> e.getIn().setHeader(SecurityHubConstants.FILTERS, filters));

        assertNull(exchange.getException());
        ArgumentCaptor<GetFindingsRequest> captor = ArgumentCaptor.forClass(GetFindingsRequest.class);
        verify(securityHubClient).getFindings(captor.capture());
        assertEquals(filters, captor.getValue().filters());
    }

    @Test
    public void getFindingsFailsOnFiltersAsJsonText() {
        Exchange exchange = template.send("direct:getFindings",
                e -> e.getIn().setHeader(SecurityHubConstants.FILTERS, "{\"SeverityLabel\": [{\"Value\": \"HIGH\"}]}"));

        IllegalArgumentException iae = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(iae.getMessage().contains(SecurityHubConstants.FILTERS), iae.getMessage());
        assertTrue(iae.getMessage().contains(AwsSecurityFindingFilters.class.getName()), iae.getMessage());
        assertTrue(iae.getMessage().contains("pojoRequest"), iae.getMessage());
        verify(securityHubClient, never()).getFindings(any(GetFindingsRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = { SecurityHubConstants.NOTE, SecurityHubConstants.SEVERITY, SecurityHubConstants.WORKFLOW })
    public void batchUpdateFindingsFailsOnUpdateHeaderOfAnotherType(String header) {
        Exchange exchange = template.send("direct:batchUpdateFindings", e -> {
            e.getIn().setHeader(SecurityHubConstants.FINDING_IDENTIFIERS,
                    List.of(AwsSecurityFindingIdentifier.builder().id("f-1").productArn("arn:p").build()));
            e.getIn().setHeader(header, "{\"Text\": \"some text\"}");
        });

        IllegalArgumentException iae = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(iae.getMessage().contains(header), iae.getMessage());
        verify(securityHubClient, never()).batchUpdateFindings(any(BatchUpdateFindingsRequest.class));
    }

    @Test
    public void batchUpdateFindingsWithoutUpdateHeaders() {
        when(securityHubClient.batchUpdateFindings(any(BatchUpdateFindingsRequest.class)))
                .thenReturn(BatchUpdateFindingsResponse.builder().build());

        Exchange exchange = template.send("direct:batchUpdateFindings",
                e -> e.getIn().setHeader(SecurityHubConstants.FINDING_IDENTIFIERS,
                        List.of(AwsSecurityFindingIdentifier.builder().id("f-1").productArn("arn:p").build())));

        assertNull(exchange.getException());
        verify(securityHubClient).batchUpdateFindings(any(BatchUpdateFindingsRequest.class));
    }

    @Test
    public void batchImportFindingsTakesAnArrayInTheBody() {
        mockImportSuccess();
        AwsSecurityFinding f1 = finding("f-1");
        AwsSecurityFinding f2 = finding("f-2");

        Exchange exchange = template.send("direct:batchImportFindings",
                e -> e.getIn().setBody(new AwsSecurityFinding[] { f1, f2 }));

        assertNull(exchange.getException());
        assertEquals(List.of(f1, f2), capturedImport().findings());
    }

    @Test
    public void batchImportFindingsTakesASetInTheBody() {
        mockImportSuccess();
        AwsSecurityFinding f1 = finding("f-1");
        AwsSecurityFinding f2 = finding("f-2");
        Set<AwsSecurityFinding> findings = new LinkedHashSet<>(List.of(f1, f2));

        Exchange exchange = template.send("direct:batchImportFindings", e -> e.getIn().setBody(findings));

        assertNull(exchange.getException());
        assertEquals(List.of(f1, f2), capturedImport().findings());
    }

    @Test
    public void batchImportFindingsFailsOnAnElementOfAnotherType() {
        Exchange exchange = template.send("direct:batchImportFindings",
                e -> e.getIn().setBody(List.of(finding("f-1"), "not a finding")));

        IllegalArgumentException iae = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(iae.getMessage().contains(AwsSecurityFinding.class.getName()), iae.getMessage());
        assertTrue(iae.getMessage().contains(String.class.getName()), iae.getMessage());
        verify(securityHubClient, never()).batchImportFindings(any(BatchImportFindingsRequest.class));
    }

    @Test
    public void batchImportFindingsFailsWhenSecurityHubReportsFailedFindings() {
        when(securityHubClient.batchImportFindings(any(BatchImportFindingsRequest.class)))
                .thenReturn(BatchImportFindingsResponse.builder()
                        .successCount(1)
                        .failedCount(1)
                        .failedFindings(ImportFindingsError.builder()
                                .id("f-2").errorCode("InvalidInput").errorMessage("Missing Title").build())
                        .build());

        Exchange exchange = template.send("direct:batchImportFindings",
                e -> e.getIn().setBody(List.of(finding("f-1"), finding("f-2"))));

        IllegalStateException ise = assertInstanceOf(IllegalStateException.class, exchange.getException());
        assertTrue(ise.getMessage().contains("f-2"), ise.getMessage());
        assertTrue(ise.getMessage().contains("InvalidInput"), ise.getMessage());
        assertTrue(ise.getMessage().contains("Missing Title"), ise.getMessage());
        assertEquals(1, exchange.getMessage().getHeader(SecurityHubConstants.FAILED_COUNT));
        assertEquals(1, exchange.getMessage().getHeader(SecurityHubConstants.SUCCESS_COUNT));
    }

    @Test
    public void batchImportFindingsWithoutFailedFindings() {
        mockImportSuccess();

        Exchange exchange = template.send("direct:batchImportFindings", e -> e.getIn().setBody(finding("f-1")));

        assertNull(exchange.getException());
        assertEquals(0, exchange.getMessage().getHeader(SecurityHubConstants.FAILED_COUNT));
        assertEquals(1, exchange.getMessage().getHeader(SecurityHubConstants.SUCCESS_COUNT));
    }

    private void mockImportSuccess() {
        when(securityHubClient.batchImportFindings(any(BatchImportFindingsRequest.class)))
                .thenReturn(BatchImportFindingsResponse.builder().successCount(1).failedCount(0).build());
    }

    private BatchImportFindingsRequest capturedImport() {
        ArgumentCaptor<BatchImportFindingsRequest> captor = ArgumentCaptor.forClass(BatchImportFindingsRequest.class);
        verify(securityHubClient).batchImportFindings(captor.capture());
        return captor.getValue();
    }

    private static AwsSecurityFinding finding(String id) {
        return AwsSecurityFinding.builder().id(id).title("Title " + id).build();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:getFindings")
                        .to("aws-security-hub://test?securityHubClient=#securityHubClient&operation=getFindings");
                from("direct:batchUpdateFindings")
                        .to("aws-security-hub://test?securityHubClient=#securityHubClient&operation=batchUpdateFindings");
                from("direct:batchImportFindings")
                        .to("aws-security-hub://test?securityHubClient=#securityHubClient&operation=batchImportFindings");
            }
        };
    }
}
