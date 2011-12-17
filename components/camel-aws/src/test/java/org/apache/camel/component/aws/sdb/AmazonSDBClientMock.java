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
package org.apache.camel.component.aws.sdb;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.CreateDomainRequest;
import com.amazonaws.services.simpledb.model.DeleteAttributesRequest;
import com.amazonaws.services.simpledb.model.DomainMetadataRequest;
import com.amazonaws.services.simpledb.model.DomainMetadataResult;
import com.amazonaws.services.simpledb.model.GetAttributesRequest;
import com.amazonaws.services.simpledb.model.GetAttributesResult;
import com.amazonaws.services.simpledb.model.NoSuchDomainException;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;

public class AmazonSDBClientMock extends AmazonSimpleDBClient {
    private String domainNameToCreate;
    private String itemNameToDelete;
    private PutAttributesRequest putAttributesRequest;

    public AmazonSDBClientMock() {
        super(new BasicAWSCredentials("user", "secret"));
    }

    @Override
    public DomainMetadataResult domainMetadata(DomainMetadataRequest domainMetadataRequest) throws AmazonServiceException, AmazonClientException {
        throw new NoSuchDomainException(domainNameToCreate + " doesn't exist");
    }

    @Override
    public void createDomain(CreateDomainRequest createDomainRequest) throws AmazonServiceException, AmazonClientException {
        this.domainNameToCreate = createDomainRequest.getDomainName();
    }

    @Override
    public void putAttributes(PutAttributesRequest putAttributesRequest) throws AmazonServiceException, AmazonClientException {
        this.putAttributesRequest = putAttributesRequest;
    }

    @Override
    public void deleteAttributes(DeleteAttributesRequest deleteAttributesRequest) throws AmazonServiceException, AmazonClientException {
        String domainName = deleteAttributesRequest.getDomainName();
        if ("MissingDomain".equals(domainName)) {
            throw new NoSuchDomainException(domainName);
        }
        this.itemNameToDelete = deleteAttributesRequest.getItemName();
    }

    @Override
    public GetAttributesResult getAttributes(GetAttributesRequest getAttributesRequest) throws AmazonServiceException, AmazonClientException {
        return new GetAttributesResult()
                .withAttributes(new Attribute("AttributeOne", "Value One"))
                .withAttributes(new Attribute("AttributeTwo", "Value Two"));
    }

    public PutAttributesRequest getPutAttributesRequest() {
        return putAttributesRequest;
    }

    public String getDomainNameToCreate() {
        return domainNameToCreate;
    }

    public String getItemNameToDelete() {
        return itemNameToDelete;
    }
}
