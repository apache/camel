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
package org.apache.camel.component.influxdb2;

import java.util.List;

import com.influxdb.client.BucketsQuery;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.OrganizationsQuery;
import com.influxdb.client.domain.Bucket;
import com.influxdb.client.domain.Organization;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.exceptions.NotFoundException;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.influxdb2.enums.Operation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interact with <a href="https://influxdata.com/time-series-platform/influxdb/">InfluxDB</a> v2, a time series
 * database.
 */
@UriEndpoint(firstVersion = "3.20.0", scheme = "influxdb2", title = "InfluxDB2",
             syntax = "influxdb2:connectionBean", category = { Category.DATABASE },
             producerOnly = true, headersClass = InfluxDb2Constants.class)
public class InfluxDb2Endpoint extends DefaultEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(InfluxDb2Endpoint.class);

    private InfluxDBClient influxDBClient;

    @UriPath
    @Metadata(required = true,
              description = "Connection to the Influx database, of class com.influxdb.client.InfluxDBClient.class.")
    private String connectionBean;
    @UriParam
    @Metadata(required = true, description = "The name of the organization where the time series will be stored.")
    private String org;
    @UriParam
    @Metadata(required = true, description = "The name of the bucket where the time series will be stored.")
    private String bucket;
    @UriParam(defaultValue = "default", description = "Define the retention policy to the data created by the endpoint.")
    private String retentionPolicy = "default";

    @UriParam(defaultValue = "INSERT", description = "Define if this operation is an insert of ping.")
    private Operation operation = Operation.INSERT;
    @UriParam(defaultValue = "true", description = "Define if we want to auto create the organization if it's not present.")
    private boolean autoCreateOrg = true;

    @UriParam(defaultValue = "true", description = "Define if we want to auto create the bucket if it's not present.")
    private boolean autoCreateBucket = true;

    @UriParam(defaultValue = "ms",
              description = "The format or precision of time series timestamps.")
    private WritePrecision writePrecision = WritePrecision.MS;
    private String orgID;

    public InfluxDb2Endpoint(String uri, InfluxDb2Component component) {
        super(uri, component);
    }

    public InfluxDb2Endpoint() {
    }

    public InfluxDBClient getInfluxDBClient() {
        return influxDBClient;
    }

    public void setInfluxDBClient(InfluxDBClient influxDBClient) {
        this.influxDBClient = influxDBClient;
    }

    public String getConnectionBean() {
        return connectionBean;
    }

    public void setConnectionBean(String connectionBean) {
        this.connectionBean = connectionBean;
    }

    public String getOrg() {
        return org;
    }

    public void setOrg(String org) {
        this.org = org;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRetentionPolicy() {
        return retentionPolicy;
    }

    public void setRetentionPolicy(String retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
    }

    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    public boolean isAutoCreateOrg() {
        return autoCreateOrg;
    }

    public void setAutoCreateOrg(boolean autoCreateOrg) {
        this.autoCreateOrg = autoCreateOrg;
    }

    public boolean isAutoCreateBucket() {
        return autoCreateBucket;
    }

    public void setAutoCreateBucket(boolean autoCreateBucket) {
        this.autoCreateBucket = autoCreateBucket;
    }

    public String getOrgID() {
        return orgID;
    }

    public void setOrgID(String orgID) {
        this.orgID = orgID;
    }

    public WritePrecision getWritePrecision() {
        return writePrecision;
    }

    public void setWritePrecision(WritePrecision writePrecision) {
        this.writePrecision = writePrecision;
    }

    public Producer createProducer() throws Exception {
        return new InfluxDb2Producer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        ensureOrgExists();
        ensureBucketExists();
    }

    private void ensureOrgExists() {
        Organization organization = null;
        OrganizationsQuery organizationsQuery = new OrganizationsQuery();
        organizationsQuery.setOrg(org);
        boolean exists = false;
        try {
            List<Organization> organizations = getInfluxDBClient().getOrganizationsApi().findOrganizations(organizationsQuery);
            if (organizations.stream().anyMatch(o -> o.getName().equals(org))) {
                exists = true;
                organization = organizations.stream().filter(o -> o.getName().equals(org)).findFirst().get();
            }
        } catch (NotFoundException ex) {
            exists = false;
        }
        if (!exists && autoCreateOrg) {
            LOG.debug("Organization {} doesn't exist. Creating it...", org);
            organization = getInfluxDBClient().getOrganizationsApi().createOrganization(org);
        }
        if (organization != null) {
            setOrgID(organization.getId());
        }
    }

    private void ensureBucketExists() {

        ensureOrgExists();

        boolean exists = false;
        BucketsQuery bucketsQuery = new BucketsQuery();
        bucketsQuery.setOrg(org);
        bucketsQuery.setName(bucket);
        try {
            List<Bucket> buckets = getInfluxDBClient().getBucketsApi().findBuckets(bucketsQuery);
            if (buckets.stream().anyMatch(b -> b.getName().equals(bucket))) {
                exists = true;
            }
        } catch (NotFoundException ex) {
            exists = false;
        }
        if (!exists && autoCreateBucket) {
            LOG.debug("Bucket {} doesn't exist. Creating it...", bucket);
            getInfluxDBClient().getBucketsApi().createBucket(bucket, getOrgID());
        }
    }
}
