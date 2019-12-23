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
package org.apache.camel.component.salesforce.api.dto.bulk;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;

/**
 * This object contains factory methods for each Java content interface and Java
 * element interface in the org.apache.camel.component.salesforce.api.dto.bulk
 * package.
 * <p>
 * An ObjectFactory allows you to programatically construct new instances of the
 * Java representation for XML content. The Java representation of XML content
 * can consist of schema derived interfaces and classes representing the binding
 * of schema type definitions, element declarations and model groups. Factory
 * methods for each of these are provided in this class.
 */
@XmlRegistry
public class ObjectFactory {

    private static final QName JOB_INFO_QNAME = new QName("http://www.force.com/2009/06/asyncapi/dataload", "jobInfo");
    private static final QName BATCH_INFO_QNAME = new QName("http://www.force.com/2009/06/asyncapi/dataload", "batchInfo");
    private static final QName ERROR_QNAME = new QName("http://www.force.com/2009/06/asyncapi/dataload", "error");
    private static final QName RESULTS_QNAME = new QName("http://www.force.com/2009/06/asyncapi/dataload", "results");
    private static final QName RESULT_LIST_QNAME = new QName("http://www.force.com/2009/06/asyncapi/dataload", "result-list");
    private static final QName BATCH_INFO_LIST_QNAME = new QName("http://www.force.com/2009/06/asyncapi/dataload", "batchInfoList");
    private static final QName QUERY_RESULT_QNAME = new QName("http://www.force.com/2009/06/asyncapi/dataload", "queryResult");

    /**
     * Create a new ObjectFactory that can be used to create new instances of
     * schema derived classes for package:
     * org.apache.camel.component.salesforce.api.dto.bulk
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link SObject }
     */
    public SObject createSObject() {
        return new SObject();
    }

    /**
     * Create an instance of {@link ResultError }
     */
    public ResultError createResultError() {
        return new ResultError();
    }

    /**
     * Create an instance of {@link BatchInfo }
     */
    public BatchInfo createBatchInfo() {
        return new BatchInfo();
    }

    /**
     * Create an instance of {@link BatchResult }
     */
    public BatchResult createBatchResult() {
        return new BatchResult();
    }

    /**
     * Create an instance of {@link QueryResultList }
     */
    public QueryResultList createQueryResultList() {
        return new QueryResultList();
    }

    /**
     * Create an instance of {@link Error }
     */
    public Error createError() {
        return new Error();
    }

    /**
     * Create an instance of {@link BatchInfoList }
     */
    public BatchInfoList createBatchInfoList() {
        return new BatchInfoList();
    }

    /**
     * Create an instance of {@link Result }
     */
    public Result createResult() {
        return new Result();
    }

    /**
     * Create an instance of {@link JobInfo }
     */
    public JobInfo createJobInfo() {
        return new JobInfo();
    }

    /**
     * Create an instance of {@link QueryResult }
     */
    public QueryResult createQueryResult() {
        return new QueryResult();
    }

    /**
     * Create an instance of
     * {@link javax.xml.bind.JAXBElement }{@code <}{@link JobInfo }{@code >}}
     */
    @XmlElementDecl(namespace = "http://www.force.com/2009/06/asyncapi/dataload", name = "jobInfo")
    public JAXBElement<JobInfo> createJobInfo(JobInfo value) {
        return new JAXBElement<>(JOB_INFO_QNAME, JobInfo.class, null, value);
    }

    /**
     * Create an instance of
     * {@link javax.xml.bind.JAXBElement }{@code <}{@link BatchInfo }{@code >}}
     */
    @XmlElementDecl(namespace = "http://www.force.com/2009/06/asyncapi/dataload", name = "batchInfo")
    public JAXBElement<BatchInfo> createBatchInfo(BatchInfo value) {
        return new JAXBElement<>(BATCH_INFO_QNAME, BatchInfo.class, null, value);
    }

    /**
     * Create an instance of
     * {@link javax.xml.bind.JAXBElement }{@code <}{@link Error }{@code >}}
     */
    @XmlElementDecl(namespace = "http://www.force.com/2009/06/asyncapi/dataload", name = "error")
    public JAXBElement<Error> createError(Error value) {
        return new JAXBElement<>(ERROR_QNAME, Error.class, null, value);
    }

    /**
     * Create an instance of
     * {@link javax.xml.bind.JAXBElement }{@code <}{@link BatchResult
     * }{@code >}}
     */
    @XmlElementDecl(namespace = "http://www.force.com/2009/06/asyncapi/dataload", name = "results")
    public JAXBElement<BatchResult> createResults(BatchResult value) {
        return new JAXBElement<>(RESULTS_QNAME, BatchResult.class, null, value);
    }

    /**
     * Create an instance of
     * {@link javax.xml.bind.JAXBElement }{@code <}{@link QueryResultList
     * }{@code >}}
     */
    @XmlElementDecl(namespace = "http://www.force.com/2009/06/asyncapi/dataload", name = "result-list")
    public JAXBElement<QueryResultList> createResultList(QueryResultList value) {
        return new JAXBElement<>(RESULT_LIST_QNAME, QueryResultList.class, null, value);
    }

    /**
     * Create an instance of
     * {@link javax.xml.bind.JAXBElement }{@code <}{@link BatchInfoList
     * }{@code >}}
     */
    @XmlElementDecl(namespace = "http://www.force.com/2009/06/asyncapi/dataload", name = "batchInfoList")
    public JAXBElement<BatchInfoList> createBatchInfoList(BatchInfoList value) {
        return new JAXBElement<>(BATCH_INFO_LIST_QNAME, BatchInfoList.class, null, value);
    }

    /**
     * Create an instance of
     * {@link javax.xml.bind.JAXBElement }{@code <}{@link QueryResult
     * }{@code >}}
     */
    @XmlElementDecl(namespace = "http://www.force.com/2009/06/asyncapi/dataload", name = "queryResult")
    public JAXBElement<QueryResult> createQueryResult(QueryResult value) {
        return new JAXBElement<>(QUERY_RESULT_QNAME, QueryResult.class, null, value);
    }

}
