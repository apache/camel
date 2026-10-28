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

    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsSecurityHubOperation";

    @Metadata(description = "The findings to import, as a List of AwsSecurityFinding. When not set, the findings are taken from the body: a Collection or an array of AwsSecurityFinding, or one AwsSecurityFinding.",
              javaType = "List<AwsSecurityFinding>")
    String FINDINGS = "CamelAwsSecurityHubFindings";

    @Metadata(description = "The finding identifiers for batch update", javaType = "List<AwsSecurityFindingIdentifier>")
    String FINDING_IDENTIFIERS = "CamelAwsSecurityHubFindingIdentifiers";

    @Metadata(description = "The filters to apply when retrieving findings. It must be an AwsSecurityFindingFilters object; a value of another type, such as JSON text, fails the exchange.",
              javaType = "AwsSecurityFindingFilters")
    String FILTERS = "CamelAwsSecurityHubFilters";

    @Metadata(description = "The note to add to findings during update. It must be a NoteUpdate object; a value of another type fails the exchange.",
              javaType = "NoteUpdate")
    String NOTE = "CamelAwsSecurityHubNote";

    @Metadata(description = "The severity to set on findings during update. It must be a SeverityUpdate object; a value of another type fails the exchange.",
              javaType = "SeverityUpdate")
    String SEVERITY = "CamelAwsSecurityHubSeverity";

    @Metadata(description = "The workflow status to set on findings. It must be a WorkflowUpdate object; a value of another type fails the exchange.",
              javaType = "WorkflowUpdate")
    String WORKFLOW = "CamelAwsSecurityHubWorkflow";

    @Metadata(description = "The verification state to set on findings", javaType = "String")
    String VERIFICATION_STATE = "CamelAwsSecurityHubVerificationState";

    @Metadata(description = "The confidence level to set on findings", javaType = "Integer")
    String CONFIDENCE = "CamelAwsSecurityHubConfidence";

    @Metadata(description = "The criticality level to set on findings", javaType = "Integer")
    String CRITICALITY = "CamelAwsSecurityHubCriticality";

    @Metadata(description = "User-defined fields to add to findings", javaType = "Map<String, String>")
    String USER_DEFINED_FIELDS = "CamelAwsSecurityHubUserDefinedFields";

    @Metadata(description = "Related findings to associate", javaType = "List<RelatedFinding>")
    String RELATED_FINDINGS = "CamelAwsSecurityHubRelatedFindings";

    @Metadata(description = "The types to assign to findings", javaType = "List<String>")
    String TYPES = "CamelAwsSecurityHubTypes";

    // Pagination constants
    @Metadata(label = "getFindings listEnabledProductsForImport getFindingHistory",
              description = "The token for the next set of results. It is both an input and an output: the operation sends it and then sets it to the token of the response, so remove it before calling a different list operation in the same route.",
              javaType = "String")
    String NEXT_TOKEN = "CamelAwsSecurityHubNextToken";

    @Metadata(label = "getFindings",
              description = "The maximum number of results to return", javaType = "Integer")
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
              description = "The list of findings that were not updated",
              javaType = "List<BatchUpdateFindingsUnprocessedFinding>")
    String UNPROCESSED_FINDINGS = "CamelAwsSecurityHubUnprocessedFindings";

    @Metadata(label = "batchUpdateFindings",
              description = "The list of findings that were updated successfully",
              javaType = "List<AwsSecurityFindingIdentifier>")
    String PROCESSED_FINDINGS = "CamelAwsSecurityHubProcessedFindings";

    @Metadata(label = "getFindingHistory",
              description = "The finding ID to get history for", javaType = "String")
    String FINDING_ID = "CamelAwsSecurityHubFindingId";

    @Metadata(label = "getFindingHistory",
              description = "The product ARN for the finding", javaType = "String")
    String PRODUCT_ARN = "CamelAwsSecurityHubProductArn";

    @Metadata(label = "getFindingAggregator",
              description = "The ARN of the finding aggregator to retrieve", javaType = "String")
    String FINDING_AGGREGATOR_ARN = "CamelAwsSecurityHubFindingAggregatorArn";
}
