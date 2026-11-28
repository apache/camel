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

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS Security Hub component
 */
public interface SecurityHubConstants {

    @Metadata(description = "The operation that the producer performs. It replaces the operation option.", javaType = "String")
    String OPERATION = "CamelAwsSecurityHubOperation";

    @Metadata(description = "The findings that batchImportFindings imports, as a List of AwsSecurityFinding. When not set or empty, the findings are taken from the body: a Collection or an array of AwsSecurityFinding, or one AwsSecurityFinding. Security Hub accepts at most 100 findings in one request.",
              javaType = "List<software.amazon.awssdk.services.securityhub.model.AwsSecurityFinding>")
    String FINDINGS = "CamelAwsSecurityHubFindings";

    @Metadata(description = "The findings that batchUpdateFindings updates, as a List of AwsSecurityFindingIdentifier, each with the ID of a finding and the ARN of the product that generated it. batchUpdateFindings requires it, and Security Hub accepts at most 100 findings in one request.",
              javaType = "List<software.amazon.awssdk.services.securityhub.model.AwsSecurityFindingIdentifier>")
    String FINDING_IDENTIFIERS = "CamelAwsSecurityHubFindingIdentifiers";

    @Metadata(description = "The filters that select the findings that getFindings returns. It must be an AwsSecurityFindingFilters object; a value of another type, such as JSON text, fails the exchange.",
              javaType = "software.amazon.awssdk.services.securityhub.model.AwsSecurityFindingFilters")
    String FILTERS = "CamelAwsSecurityHubFilters";

    @Metadata(description = "The note that batchUpdateFindings sets on the findings, as a NoteUpdate with the text of the note and the name of who updated it. It must be a NoteUpdate object; a value of another type fails the exchange.",
              javaType = "software.amazon.awssdk.services.securityhub.model.NoteUpdate")
    String NOTE = "CamelAwsSecurityHubNote";

    @Metadata(description = "The severity that batchUpdateFindings sets on the findings, as a SeverityUpdate, for example with the label HIGH. It must be a SeverityUpdate object; a value of another type fails the exchange.",
              javaType = "software.amazon.awssdk.services.securityhub.model.SeverityUpdate")
    String SEVERITY = "CamelAwsSecurityHubSeverity";

    @Metadata(description = "The workflow status that batchUpdateFindings sets on the findings, as a WorkflowUpdate with the status NEW, NOTIFIED, RESOLVED or SUPPRESSED. It must be a WorkflowUpdate object; a value of another type fails the exchange.",
              javaType = "software.amazon.awssdk.services.securityhub.model.WorkflowUpdate")
    String WORKFLOW = "CamelAwsSecurityHubWorkflow";

    @Metadata(description = "The verification state that batchUpdateFindings sets on the findings: UNKNOWN, TRUE_POSITIVE, FALSE_POSITIVE or BENIGN_POSITIVE.",
              javaType = "String")
    String VERIFICATION_STATE = "CamelAwsSecurityHubVerificationState";

    @Metadata(description = "The confidence that batchUpdateFindings sets on the findings: the likelihood, from 0 to 100, that a finding identifies the behavior or issue that it is meant to identify.",
              javaType = "Integer")
    String CONFIDENCE = "CamelAwsSecurityHubConfidence";

    @Metadata(description = "The criticality that batchUpdateFindings sets on the findings: the importance, from 0 to 100, of the resources of a finding.",
              javaType = "Integer")
    String CRITICALITY = "CamelAwsSecurityHubCriticality";

    @Metadata(description = "The custom name and value pairs that batchUpdateFindings sets on the findings, as a Map of String to String.",
              javaType = "Map<String, String>")
    String USER_DEFINED_FIELDS = "CamelAwsSecurityHubUserDefinedFields";

    @Metadata(description = "The findings that batchUpdateFindings relates to the updated findings, as a List of RelatedFinding, each with the ID of a finding and the ARN of the product that generated it.",
              javaType = "List<software.amazon.awssdk.services.securityhub.model.RelatedFinding>")
    String RELATED_FINDINGS = "CamelAwsSecurityHubRelatedFindings";

    @Metadata(description = "The finding types that batchUpdateFindings sets on the findings, each in the format namespace/category/classifier.",
              javaType = "List<String>")
    String TYPES = "CamelAwsSecurityHubTypes";

    // Pagination constants
    @Metadata(label = "getFindings listEnabledProductsForImport getFindingHistory",
              description = "The token for the next set of results. It is both an input and an output: the operation sends it and then sets it to the token of the response, so remove it before calling a different list operation in the same route.",
              javaType = "String")
    String NEXT_TOKEN = "CamelAwsSecurityHubNextToken";

    @Metadata(label = "getFindings",
              description = "The maximum number of results in one page of getFindings, getFindingHistory or listEnabledProductsForImport. Security Hub accepts a value of at most 100.",
              javaType = "Integer")
    String MAX_RESULTS = "CamelAwsSecurityHubMaxResults";

    // Response metadata
    @Metadata(label = "batchImportFindings",
              description = "The count of findings that failed to import. When it is more than zero, the exchange fails with an exception naming the failed findings.",
              javaType = "Integer")
    String FAILED_COUNT = "CamelAwsSecurityHubFailedCount";

    @Metadata(label = "batchImportFindings",
              description = "The count of findings that were successfully imported", javaType = "Integer")
    String SUCCESS_COUNT = "CamelAwsSecurityHubSuccessCount";

    @Metadata(label = "batchUpdateFindings",
              description = "The findings that batchUpdateFindings did not update, each with an error code and an error message. Unlike a finding that batchImportFindings does not import, they do not fail the exchange.",
              javaType = "List<software.amazon.awssdk.services.securityhub.model.BatchUpdateFindingsUnprocessedFinding>")
    String UNPROCESSED_FINDINGS = "CamelAwsSecurityHubUnprocessedFindings";

    @Metadata(label = "batchUpdateFindings",
              description = "The list of findings that were updated successfully",
              javaType = "List<software.amazon.awssdk.services.securityhub.model.AwsSecurityFindingIdentifier>")
    String PROCESSED_FINDINGS = "CamelAwsSecurityHubProcessedFindings";

    @Metadata(label = "getFindingHistory",
              description = "The ID of the finding whose history getFindingHistory returns. getFindingHistory requires it, together with the CamelAwsSecurityHubProductArn header.",
              javaType = "String")
    String FINDING_ID = "CamelAwsSecurityHubFindingId";

    @Metadata(label = "getFindingHistory",
              description = "The ARN of the product that generated the finding whose history getFindingHistory returns. getFindingHistory requires it, together with the CamelAwsSecurityHubFindingId header.",
              javaType = "String")
    String PRODUCT_ARN = "CamelAwsSecurityHubProductArn";

    @Metadata(label = "getFindingAggregator",
              description = "The ARN of the finding aggregator whose configuration getFindingAggregator returns. getFindingAggregator requires it.",
              javaType = "String")
    String FINDING_AGGREGATOR_ARN = "CamelAwsSecurityHubFindingAggregatorArn";
}
