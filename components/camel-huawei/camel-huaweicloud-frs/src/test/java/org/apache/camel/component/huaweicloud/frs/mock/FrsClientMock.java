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
package org.apache.camel.component.huaweicloud.frs.mock;

import java.util.*;

import com.huaweicloud.sdk.core.HcClient;
import com.huaweicloud.sdk.frs.v2.FrsClient;
import com.huaweicloud.sdk.frs.v2.model.CompareFaceByBase64Request;
import com.huaweicloud.sdk.frs.v2.model.CompareFaceByBase64Response;
import com.huaweicloud.sdk.frs.v2.model.CompareFaceByFileRequest;
import com.huaweicloud.sdk.frs.v2.model.CompareFaceByFileResponse;
import com.huaweicloud.sdk.frs.v2.model.CompareFaceByUrlRequest;
import com.huaweicloud.sdk.frs.v2.model.CompareFaceByUrlResponse;
import com.huaweicloud.sdk.frs.v2.model.DetectFaceByBase64Request;
import com.huaweicloud.sdk.frs.v2.model.DetectFaceByBase64Response;
import com.huaweicloud.sdk.frs.v2.model.DetectFaceByFileRequest;
import com.huaweicloud.sdk.frs.v2.model.DetectFaceByFileResponse;
import com.huaweicloud.sdk.frs.v2.model.DetectFaceByUrlRequest;
import com.huaweicloud.sdk.frs.v2.model.DetectFaceByUrlResponse;
import com.huaweicloud.sdk.frs.v2.model.DetectLiveByBase64Request;
import com.huaweicloud.sdk.frs.v2.model.DetectLiveByBase64Response;
import com.huaweicloud.sdk.frs.v2.model.DetectLiveByFileRequest;
import com.huaweicloud.sdk.frs.v2.model.DetectLiveByFileResponse;
import com.huaweicloud.sdk.frs.v2.model.DetectLiveByUrlRequest;
import com.huaweicloud.sdk.frs.v2.model.DetectLiveByUrlResponse;

public class FrsClientMock extends FrsClient {
    public FrsClientMock(HcClient hcClient) {
        super(null);
    }

    @Override
    public DetectFaceByBase64Response detectFaceByBase64(DetectFaceByBase64Request request) {
        return new DetectFaceByBase64Response().withFaces(MockResult.getFaceDetectionResult());
    }

    @Override
    public DetectFaceByFileResponse detectFaceByFile(DetectFaceByFileRequest request) {
        return new DetectFaceByFileResponse().withFaces(MockResult.getFaceDetectionResult());
    }

    @Override
    public DetectFaceByUrlResponse detectFaceByUrl(DetectFaceByUrlRequest request) {
        return new DetectFaceByUrlResponse().withFaces(MockResult.getFaceDetectionResult());
    }

    @Override
    public CompareFaceByBase64Response compareFaceByBase64(CompareFaceByBase64Request request) {
        return new CompareFaceByBase64Response().withImage1Face(MockResult.getCompareFaceResult())
                .withImage2Face(MockResult.getCompareFaceResult()).withSimilarity(1.0);
    }

    @Override
    public CompareFaceByUrlResponse compareFaceByUrl(CompareFaceByUrlRequest request) {
        return new CompareFaceByUrlResponse().withImage1Face(MockResult.getCompareFaceResult())
                .withImage2Face(MockResult.getCompareFaceResult()).withSimilarity(1.0);
    }

    @Override
    public CompareFaceByFileResponse compareFaceByFile(CompareFaceByFileRequest request) {
        return new CompareFaceByFileResponse().withImage1Face(MockResult.getCompareFaceResult())
                .withImage2Face(MockResult.getCompareFaceResult()).withSimilarity(1.0);
    }

    @Override
    public DetectLiveByBase64Response detectLiveByBase64(DetectLiveByBase64Request request) {
        return new DetectLiveByBase64Response().withVideoResult(MockResult.getLiveDetectResult())
                .withWarningList(Collections.emptyList());
    }

    @Override
    public DetectLiveByUrlResponse detectLiveByUrl(DetectLiveByUrlRequest request) {
        return new DetectLiveByUrlResponse().withVideoResult(MockResult.getLiveDetectResult())
                .withWarningList(Collections.emptyList());
    }

    @Override
    public DetectLiveByFileResponse detectLiveByFile(DetectLiveByFileRequest request) {
        return new DetectLiveByFileResponse().withVideoResult(MockResult.getLiveDetectResult())
                .withWarningList(Collections.emptyList());
    }

}
