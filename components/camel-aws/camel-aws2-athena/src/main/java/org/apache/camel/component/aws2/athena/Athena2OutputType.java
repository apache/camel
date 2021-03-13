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

import software.amazon.awssdk.services.athena.paginators.GetQueryResultsIterable;

/**
 * Represents various ways to return query results from Athena. For example, choose between a streaming iterator that
 * will eventually yield all results, a static list of rows and next token, or a pointer to the results in S3.
 */
public enum Athena2OutputType {
    /**
     * When using an endpoint that returns rows directly back to the caller, such as {@code getQueryResults}, use AWS 2
     * Athena {@link GetQueryResultsIterable} to return a streaming list of results. Returning a streaming result means
     * that no API requests happen until the streaming result is accessed the first time.
     *
     * <p>
     * This is the type to use if you need to process large result sets in memory (as opposed to in another process like
     * an EMR job), as the iterable returned using this method will stream results from AWS a page at a time, thus
     * limiting the amount of memory consumed at any one point in time.
     */
    StreamList,

    /**
     * Return a static list of rows. The amount of rows returned is limited to the max response size of Athena's
     * {@code GetQueryResults} (currently 1,000). Will also set the {@link Athena2Constants#NEXT_TOKEN} header to allow
     * access to the next page of results.
     *
     * @see <a href=
     *      "https://docs.aws.amazon.com/athena/latest/APIReference/API_GetQueryResults.html">GetQueryResults</a>
     */
    SelectList,

    /**
     * Return the path to the results in S3. This may be preferred if you want to pass a pointer to the results to
     * another process for handling.
     */
    S3Pointer
}
