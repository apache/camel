/*
 * Copyright 2014 claus.straube.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *//*
 * Copyright 2014 claus.straube.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.dataformat.code;

import com.google.zxing.BarcodeFormat;

/**
 * Header Variables for the qr-code data format.
 * 
 * @author claus.straube
 */
public interface QRCode {
    
    /**
     * Width of the qr-code image.
     */
    public final static String WIDTH = "QRCodeWidth";
    
    /**
     * Height of the qr-code image.
     */
    public final static String HEIGHT = "QRCodeHeight";
    
    /**
     * Type of the qr-code image (please use {@link ImageType})
     */
    public final static String IMAGE_TYPE = "QRCodeImageType";
    
    /**
     * Format of the barcode (please use {@link BarcodeFormat})
     */
    public final static String BARCODE_FORMAT = "QRCodeBarcodeFormat";
    
    /**
     * Type of the qr-code encoding.
     */
    public final static String ENCODING = "QRCodeEncoding";
    
    /**
     * Name of the image file.
     */
    public final static String NAME = "QRCodeName";
}
