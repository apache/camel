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
package com.braintreegateway;

import com.braintreegateway.exceptions.NotFoundException;
import com.braintreegateway.util.Http;
import com.braintreegateway.util.NodeWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides methods to interact with {@link Dispute} objects.
 * This class does not need to be instantiated directly. Instead, use
 * {@link BraintreeGateway#dispute()} to get an instance of this class:
 *
 * <pre>
 * BraintreeGateway gateway = new BraintreeGateway(...);
 * gateway.dispute().find(...)
 * </pre>
 *
 * For more detailed information on {@link Dispute disputes}, see <a href="https://developers.braintreepayments.com/reference/response/dispute/java" target="_blank">https://developers.braintreepayments.com/reference/response/dispute/java</a>
 */
public class DisputeGateway {
    private Configuration configuration;
    private Http http;

    public DisputeGateway(Http http, Configuration configuration) {
        this.configuration = configuration;
        this.http = http;
    }

    /**
     * Accept a @{link Dispute}, given a dispute ID.
     *
     * @param id the dispute id to accept.
     *
     * @return a {@link Result}.
     *
     * @throws NotFoundException if a Dispute with the given ID cannot be found.
     */
    public Result<Dispute> accept(String id) {
        try {
            if (id == null || id.trim().equals("")) {
                throw new NotFoundException();
            }

            NodeWrapper response = http.put(configuration.getMerchantPath() + "/disputes/" + id + "/accept");
            if (response.getElementName().equals("api-error-response")) {
                return new Result<Dispute>(response, Dispute.class);
            }

            return new Result<Dispute>();
        } catch (NotFoundException e) {
            throw new NotFoundException("dispute with id \"" + id + "\" not found");
        }
    }

    /**
     * Add File Evidence to a @{link Dispute}, given an ID and a @{link DocumentUpload} ID.
     *
     * @param disputeId the dispute id to add text evidence to.
     * @param documentId the document id of a previously uploaded document
     *
     * @return a {@link Result}.
     *
     * @throws NotFoundException if the Dispute ID or Document ID cannot be found.
     */
    public Result<DisputeEvidence> addFileEvidence(String disputeId, String documentId) {
        return addFileEvidence(disputeId, new FileEvidenceRequest().documentId(documentId));
    }

    /**
     * Add File Evidence to a @{link Dispute}, given an ID and a @{link FileEvidenceRequest} File evidence request.
     *
     * @param disputeId the dispute id to add text evidence to.
     * @param fileEvidenceRequest the file evidence request for the dispute.
     *
     * @return a {@link Result}.
     *
     * @throws NotFoundException if the Dispute ID or Document ID cannot be found.
     */
    public Result<DisputeEvidence> addFileEvidence(String disputeId, FileEvidenceRequest fileEvidenceRequest) {
        if (disputeId == null || disputeId.trim().equals("")) {
            throw new NotFoundException("dispute with id \"" + disputeId + "\" not found");
        }

        if (fileEvidenceRequest.getDocumentId() == null || fileEvidenceRequest.getDocumentId().trim().equals("")) {
            throw new NotFoundException("document with id \"" + fileEvidenceRequest.getDocumentId() + "\" not found");
        }

        try {
            NodeWrapper response = http.post(configuration.getMerchantPath() + "/disputes/" + disputeId + "/evidence", fileEvidenceRequest);
            return new Result<DisputeEvidence>(response, DisputeEvidence.class);
        } catch (NotFoundException e) {
            throw new NotFoundException("dispute with id \"" + disputeId + "\" not found");
        }
    }

    /**
     * Add Text Evidence to a @{link Dispute}, given an ID and content.
     *
     * @param id the dispute id to add text evidence to.
     * @param textEvidenceRequest the text evidence request for the dispute.
     *
     * @return a {@link Result}.
     *
     * @throws NotFoundException if a Dispute with the given ID cannot be found.
     * @throws IllegalArgumentException if the content is empty.
     */
    public Result<DisputeEvidence> addTextEvidence(String id, TextEvidenceRequest textEvidenceRequest) {
        if (textEvidenceRequest == null)
            throw new IllegalArgumentException("TextEvidenceRequest cannot be null");

        return addTextEvidenceRequest(id, textEvidenceRequest);
    }

    /**
     * Add Text Evidence to a @{link Dispute}, given an ID and content.
     *
     * @param id the dispute id to add text evidence to.
     * @param content the content of the text evidence for the dispute.
     *
     * @return a {@link Result}.
     *
     * @throws NotFoundException if a Dispute with the given ID cannot be found.
     * @throws IllegalArgumentException if the content is empty.
     */
    public Result<DisputeEvidence> addTextEvidence(String id, String content) {
        TextEvidenceRequest textEvidenceRequest = new TextEvidenceRequest().content(content);
        return addTextEvidenceRequest(id, textEvidenceRequest);
    }

    private Result<DisputeEvidence> addTextEvidenceRequest(String id, TextEvidenceRequest textEvidenceRequest) {
        String content = textEvidenceRequest.getContent();
        if (id == null || id.trim().equals("")) {
            throw new NotFoundException("Dispute ID is required");
        } else if (content == null || content.trim().equals("")) {
            throw new IllegalArgumentException("Content cannot be empty");
        }

        try {
            NodeWrapper response = http.post(configuration.getMerchantPath() + "/disputes/" + id + "/evidence", textEvidenceRequest);
            return new Result<DisputeEvidence>(response, DisputeEvidence.class);
        } catch (NotFoundException e) {
            throw new NotFoundException("Dispute with ID \"" + id + "\" not found");
        }
    }

    /**
     * Finalize a @{link Dispute}, given an ID.
     *
     * @param id the dispute id to finalize.
     *
     * @return a {@link Result}.
     *
     * @throws NotFoundException if a Dispute with the given ID cannot be found.
     */
    public Result<Dispute> finalize(String id) {
        try {
            if (id == null || id.trim().equals("")) {
                throw new NotFoundException();
            }

            NodeWrapper response = http.put(configuration.getMerchantPath() + "/disputes/" + id + "/finalize");

            if (response.getElementName().equals("api-error-response")) {
                return new Result<Dispute>(response, Dispute.class);
            }

            return new Result<Dispute>();
        } catch (NotFoundException e) {
            throw new NotFoundException("dispute with id \"" + id + "\" not found");
        }
    }

    /**
     * Returns a @{link Dispute}, given an ID.
     *
     * @param id the dispute id to find.
     *
     * @return a {@link Dispute}.
     *
     * @throws NotFoundException if a Dispute with the given ID cannot be found.
     */
    public Dispute find(String id) {
        try {
            if (id == null || id.trim().equals("")) {
                throw new NotFoundException();
            }

            return new Dispute(http.get(configuration.getMerchantPath() + "/disputes/" + id));
        } catch (NotFoundException e) {
            throw new NotFoundException("dispute with id \"" + id + "\" not found");
        }
    }

    /**
     * Remove Evidence from a @{link Dispute}, given an ID and a @{link DisputeEvidence} ID.
     *
     * @param disputeId the dispute id to remove evidence from.
     * @param evidenceId the evidence id to remove.
     *
     * @return a {@link Result}.
     *
     * @throws NotFoundException if the Dispute ID or evidence ID cannot be found.
     */
    public Result<Dispute> removeEvidence(String disputeId, String evidenceId) {
        try {
            if (disputeId == null || disputeId.trim().equals("") || evidenceId == null || evidenceId.trim().equals("")) {
                throw new NotFoundException();
            }

            NodeWrapper response = http.delete(configuration.getMerchantPath() + "/disputes/" + disputeId + "/evidence/" + evidenceId);

            if (response != null && response.getElementName().equals("api-error-response")) {
                return new Result<Dispute>(response, Dispute.class);
            }

            return new Result<Dispute>();
        } catch (NotFoundException e) {
            throw new NotFoundException("evidence with id \"" + evidenceId + "\" for dispute with id \"" + disputeId + "\" not found");
        }
    }

    /**
     * Finds all {@link Dispute}s that match the query.
     *
     * @param query the query for what disputes to find.
     *
     * @return a {@link PaginatedCollection} of {@link Dispute}.
     */
    public PaginatedCollection<Dispute> search(DisputeSearchRequest query) {
        return new PaginatedCollection<Dispute>(new DisputePager(this, query));
    }

    PaginatedResult<Dispute> fetchDisputes(DisputeSearchRequest query, int page) {
        final NodeWrapper response = http.post(configuration.getMerchantPath() + "/disputes/advanced_search?page=" + page, query);

        List<Dispute> disputes = new ArrayList<Dispute>();
        for (NodeWrapper node : response.findAll("dispute")) {
            disputes.add(new Dispute(node));
        }

        return new PaginatedResult<Dispute>(response.findInteger("total-items"), response.findInteger("page-size"), disputes);
    }
}
