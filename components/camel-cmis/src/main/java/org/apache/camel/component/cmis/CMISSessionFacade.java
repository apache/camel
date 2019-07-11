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
package org.apache.camel.component.cmis;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.DocumentType;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.ObjectType;
import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.client.api.QueryResult;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.CmisVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriParams
public class CMISSessionFacade {
    private static final Logger LOG = LoggerFactory.getLogger(CMISSessionFacade.class);
    private transient Session session;
    private final String url;
    @UriParam(
            defaultValue = "100"
    )
    private int pageSize = 100;
    @UriParam
    private int readCount;
    @UriParam
    private boolean readContent;
    @UriParam(
            label = "security",
            secret = true
    )
    private String username;
    @UriParam(
            label = "security",
            secret = true
    )
    private String password;
    @UriParam
    private String repositoryId;
    @UriParam(
            label = "consumer"
    )
    private String query;

    public CMISSessionFacade(String url) {
        this.url = url;
    }

    void initSession() {
        Map<String, String> parameter = new HashMap();
        parameter.put("org.apache.chemistry.opencmis.binding.spi.type", BindingType.ATOMPUB.value());
        parameter.put("org.apache.chemistry.opencmis.binding.atompub.url", this.url);
        parameter.put("org.apache.chemistry.opencmis.user", this.username);
        parameter.put("org.apache.chemistry.opencmis.password", this.password);
        if (this.repositoryId != null) {
            parameter.put("org.apache.chemistry.opencmis.session.repository.id", this.repositoryId);
            this.session = SessionFactoryLocator.getSessionFactory().createSession(parameter);
        } else {
            this.session = ((Repository)SessionFactoryLocator.getSessionFactory().getRepositories(parameter).get(0)).createSession();
        }

    }

    public int poll(CMISConsumer cmisConsumer) throws Exception {
        return this.query != null ? this.pollWithQuery(cmisConsumer) : this.pollTree(cmisConsumer);
    }

    private int pollTree(CMISConsumer cmisConsumer) throws Exception {
        Folder rootFolder = this.session.getRootFolder();
        RecursiveTreeWalker treeWalker = new RecursiveTreeWalker(cmisConsumer, this.readContent, this.readCount, this.pageSize);
        return treeWalker.processFolderRecursively(rootFolder);
    }

    private int pollWithQuery(CMISConsumer cmisConsumer) throws Exception {
        int count = 0;
        int pageNumber = 0;
        boolean finished = false;
        ItemIterable itemIterable = this.executeQuery(this.query);

        while(!finished) {
            ItemIterable<QueryResult> currentPage = itemIterable.skipTo((long)count).getPage();
            LOG.debug("Processing page {}", pageNumber);
            Iterator var7 = currentPage.iterator();

            while(var7.hasNext()) {
                QueryResult item = (QueryResult)var7.next();
                Map<String, Object> properties = CMISHelper.propertyDataToMap(item.getProperties());
                Object objectTypeId = item.getPropertyValueById("cmis:objectTypeId");
                InputStream inputStream = null;
                if (this.readContent && "cmis:document".equals(objectTypeId)) {
                    inputStream = this.getContentStreamFor(item);
                }

                cmisConsumer.sendExchangeWithPropsAndBody(properties, inputStream);
                ++count;
                if (count == this.readCount) {
                    finished = true;
                    break;
                }
            }

            ++pageNumber;
            if (!currentPage.getHasMoreItems()) {
                finished = true;
            }
        }

        return count;
    }

    public List<Map<String, Object>> retrieveResult(Boolean retrieveContent, Integer readSize, ItemIterable<QueryResult> itemIterable) {
        List<Map<String, Object>> result = new ArrayList();
        boolean queryForContent = retrieveContent != null ? retrieveContent : this.readContent;
        int documentsToRead = readSize != null ? readSize : this.readCount;
        int count = 0;
        int pageNumber = 0;
        boolean finished = false;

        while(!finished) {
            ItemIterable<QueryResult> currentPage = itemIterable.skipTo((long)count).getPage();
            LOG.debug("Processing page {}", pageNumber);
            Iterator var11 = currentPage.iterator();

            while(var11.hasNext()) {
                QueryResult item = (QueryResult)var11.next();
                Map<String, Object> properties = CMISHelper.propertyDataToMap(item.getProperties());
                if (queryForContent) {
                    InputStream inputStream = this.getContentStreamFor(item);
                    properties.put("CamelCMISContent", inputStream);
                }

                result.add(properties);
                ++count;
                if (count == documentsToRead) {
                    finished = true;
                    break;
                }
            }

            ++pageNumber;
            if (!currentPage.getHasMoreItems()) {
                finished = true;
            }
        }

        return result;
    }

    public ItemIterable<QueryResult> executeQuery(String query) {
        OperationContext operationContext = this.session.createOperationContext();
        operationContext.setMaxItemsPerPage(this.pageSize);
        return this.session.query(query, false, operationContext);
    }

    public Document getDocument(QueryResult queryResult) {
        if (!"cmis:document".equals(queryResult.getPropertyValueById("cmis:objectTypeId")) && !"cmis:document".equals(queryResult.getPropertyValueById("cmis:baseTypeId"))) {
            return null;
        } else {
            String objectId = (String)queryResult.getPropertyById("cmis:objectId").getFirstValue();
            return (Document)this.session.getObject(objectId);
        }
    }

    public InputStream getContentStreamFor(QueryResult item) {
        Document document = this.getDocument(item);
        if (document != null) {
            ContentStream contentStream = document.getContentStream();
            if (contentStream != null) {
                return contentStream.getStream();
            }
        }

        return null;
    }

    public CmisObject getObjectByPath(String path) {
        return this.session.getObjectByPath(path);
    }

    public CmisObject getObjectById(String id)
    {
        return this.session.getObject(id);
    }

    public boolean isObjectTypeVersionable(String objectType) {
        if ("cmis:document".equals(this.getCMISTypeFor(objectType))) {
            ObjectType typeDefinition = this.session.getTypeDefinition(objectType);
            return ((DocumentType)typeDefinition).isVersionable();
        } else {
            return false;
        }
    }

    public boolean supportsSecondaries() {
        if (this.session.getRepositoryInfo().getCmisVersion() == CmisVersion.CMIS_1_0) {
            return false;
        } else {
            Iterator var1 = this.session.getTypeChildren((String)null, false).iterator();

            ObjectType type;
            do {
                if (!var1.hasNext()) {
                    return false;
                }

                type = (ObjectType)var1.next();
            } while(!BaseTypeId.CMIS_SECONDARY.value().equals(type.getId()));

            return true;
        }
    }

    public ContentStream createContentStream(String fileName, byte[] buf, String mimeType) throws Exception {
        return buf != null ? this.session.getObjectFactory().createContentStream(fileName, (long)buf.length, mimeType, new ByteArrayInputStream(buf)) : null;
    }

    public String getCMISTypeFor(String customOrCMISType) {
        ObjectType objectBaseType = this.session.getTypeDefinition(customOrCMISType).getBaseType();
        return objectBaseType == null ? customOrCMISType : objectBaseType.getId();
    }

    public Set<String> getPropertiesFor(String objectType) {
        return this.session.getTypeDefinition(objectType).getPropertyDefinitions().keySet();
    }

    public OperationContext createOperationContext() {
        return this.session.createOperationContext();
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setRepositoryId(String repositoryId) {
        this.repositoryId = repositoryId;
    }

    public void setReadContent(boolean readContent) {
        this.readContent = readContent;
    }

    public void setReadCount(int readCount) {
        this.readCount = readCount;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
