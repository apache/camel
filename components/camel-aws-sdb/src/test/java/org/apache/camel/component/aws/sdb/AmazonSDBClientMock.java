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
package org.apache.camel.component.aws.sdb;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.simpledb.AbstractAmazonSimpleDB;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.BatchDeleteAttributesRequest;
import com.amazonaws.services.simpledb.model.BatchDeleteAttributesResult;
import com.amazonaws.services.simpledb.model.BatchPutAttributesRequest;
import com.amazonaws.services.simpledb.model.BatchPutAttributesResult;
import com.amazonaws.services.simpledb.model.CreateDomainRequest;
import com.amazonaws.services.simpledb.model.CreateDomainResult;
import com.amazonaws.services.simpledb.model.DeleteAttributesRequest;
import com.amazonaws.services.simpledb.model.DeleteAttributesResult;
import com.amazonaws.services.simpledb.model.DeleteDomainRequest;
import com.amazonaws.services.simpledb.model.DeleteDomainResult;
import com.amazonaws.services.simpledb.model.DomainMetadataRequest;
import com.amazonaws.services.simpledb.model.DomainMetadataResult;
import com.amazonaws.services.simpledb.model.GetAttributesRequest;
import com.amazonaws.services.simpledb.model.GetAttributesResult;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.ListDomainsRequest;
import com.amazonaws.services.simpledb.model.ListDomainsResult;
import com.amazonaws.services.simpledb.model.NoSuchDomainException;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.PutAttributesResult;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;

public class AmazonSDBClientMock extends AbstractAmazonSimpleDB {
    
    protected BatchDeleteAttributesRequest batchDeleteAttributesRequest;
    protected BatchPutAttributesRequest batchPutAttributesRequest;
    protected CreateDomainRequest createDomainRequest;
    protected DeleteAttributesRequest deleteAttributesRequest;
    protected DeleteDomainRequest deleteDomainRequest;
    protected DomainMetadataRequest domainMetadataRequest;
    protected GetAttributesRequest getAttributesRequest;
    protected ListDomainsRequest listDomainsRequest;
    protected PutAttributesRequest putAttributesRequest;
    protected SelectRequest selectRequest;

    public AmazonSDBClientMock() {
    }
    
    @Override
    public BatchDeleteAttributesResult batchDeleteAttributes(BatchDeleteAttributesRequest batchDeleteAttributesRequest) throws AmazonServiceException, AmazonClientException {
        this.batchDeleteAttributesRequest = batchDeleteAttributesRequest;
        return new BatchDeleteAttributesResult();
    }
    
    @Override
    public BatchPutAttributesResult batchPutAttributes(BatchPutAttributesRequest batchPutAttributesRequest) throws AmazonServiceException, AmazonClientException {
        this.batchPutAttributesRequest = batchPutAttributesRequest;
        return new BatchPutAttributesResult();
    }
    
    @Override
    public CreateDomainResult createDomain(CreateDomainRequest createDomainRequest) throws AmazonServiceException, AmazonClientException {
        this.createDomainRequest = createDomainRequest;
        return new CreateDomainResult();
    }
    
    @Override
    public DeleteAttributesResult deleteAttributes(DeleteAttributesRequest deleteAttributesRequest) throws AmazonServiceException, AmazonClientException {
        this.deleteAttributesRequest = deleteAttributesRequest;
        
        String domainName = deleteAttributesRequest.getDomainName();
        if ("MissingDomain".equals(domainName)) {
            throw new NoSuchDomainException(domainName);
        }
        return new DeleteAttributesResult();
    }
    
    @Override
    public DeleteDomainResult deleteDomain(DeleteDomainRequest deleteDomainRequest) throws AmazonServiceException, AmazonClientException {
        this.deleteDomainRequest = deleteDomainRequest;
        return new DeleteDomainResult();
    }

    @Override
    public DomainMetadataResult domainMetadata(DomainMetadataRequest domainMetadataRequest) throws AmazonServiceException, AmazonClientException {
        this.domainMetadataRequest = domainMetadataRequest;
        
        if ("NonExistingDomain".equals(domainMetadataRequest.getDomainName())) {
            throw new NoSuchDomainException("Domain 'NonExistingDomain' doesn't exist.");
        }
        
        DomainMetadataResult result = new DomainMetadataResult();
        result.setTimestamp(new Integer(10));
        result.setItemCount(new Integer(11));
        result.setAttributeNameCount(new Integer(12));
        result.setAttributeValueCount(new Integer(13));
        result.setAttributeNamesSizeBytes(new Long(1000000));
        result.setAttributeValuesSizeBytes(new Long(2000000));
        result.setItemNamesSizeBytes(new Long(3000000));
        return result;
    }
    
    @Override
    public GetAttributesResult getAttributes(GetAttributesRequest getAttributesRequest) throws AmazonServiceException, AmazonClientException {
        this.getAttributesRequest = getAttributesRequest;
        
        return new GetAttributesResult()
                .withAttributes(new Attribute("AttributeOne", "Value One"))
                .withAttributes(new Attribute("AttributeTwo", "Value Two"));
    }
    
    @Override
    public ListDomainsResult listDomains(ListDomainsRequest listDomainsRequest) throws AmazonServiceException, AmazonClientException {
        this.listDomainsRequest = listDomainsRequest;
        
        ListDomainsResult result = new ListDomainsResult();
        result.getDomainNames().add("DOMAIN1");
        result.getDomainNames().add("DOMAIN2");
        result.setNextToken("TOKEN2");
        return result;
    }

    @Override
    public PutAttributesResult putAttributes(PutAttributesRequest putAttributesRequest) throws AmazonServiceException, AmazonClientException {
        this.putAttributesRequest = putAttributesRequest;
        return new PutAttributesResult();
    }
    
    @Override
    public SelectResult select(SelectRequest selectRequest) throws AmazonServiceException, AmazonClientException {
        this.selectRequest = selectRequest;
        
        SelectResult result = new SelectResult();
        result.setNextToken("TOKEN2");
        result.getItems().add(new Item("ITEM1", null));
        result.getItems().add(new Item("ITEM2", null));
        return result;
    }
}