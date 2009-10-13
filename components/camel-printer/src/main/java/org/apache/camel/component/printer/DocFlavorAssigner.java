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
package org.apache.camel.component.printer;

import javax.print.DocFlavor;

public class DocFlavorAssigner {
    private DocFlavor d = DocFlavor.BYTE_ARRAY.AUTOSENSE;
    
    public DocFlavor forMimeTypeAUTOSENSE(String flavor) {
        if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
            d = DocFlavor.BYTE_ARRAY.AUTOSENSE;
        } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
            d = DocFlavor.INPUT_STREAM.AUTOSENSE;
        } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
            d = DocFlavor.URL.AUTOSENSE;
        }
        
        return d;
    }
    
    public DocFlavor forMimeTypeGIF(String flavor) {
        if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
            d = DocFlavor.BYTE_ARRAY.GIF;
        } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
            d = DocFlavor.INPUT_STREAM.GIF;
        } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
            d = DocFlavor.URL.GIF;
        }
    
        return d;
    }
    
    public DocFlavor forMimeTypeJPEG(String flavor) {
        if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
            d = DocFlavor.BYTE_ARRAY.JPEG;
        } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
            d = DocFlavor.INPUT_STREAM.JPEG;
        } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
            d = DocFlavor.URL.JPEG;
        }
    
        return d;
    }    

    public DocFlavor forMimeTypePDF(String flavor) {
        if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
            d = DocFlavor.BYTE_ARRAY.PDF;
        } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
            d = DocFlavor.INPUT_STREAM.PDF;
        } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
            d = DocFlavor.URL.PDF;
        }
    
        return d;
    }     
    
    public DocFlavor forMimeTypePCL(String flavor) {
        if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
            d = DocFlavor.BYTE_ARRAY.PCL;
        } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
            d = DocFlavor.INPUT_STREAM.PCL;
        } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
            d = DocFlavor.URL.PCL;
        }
    
        return d;
    }        
    
    public DocFlavor forMimeTypePOSTSCRIPT(String flavor) {
        if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
            d = DocFlavor.BYTE_ARRAY.POSTSCRIPT;
        } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
            d = DocFlavor.INPUT_STREAM.POSTSCRIPT;
        } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
            d = DocFlavor.URL.POSTSCRIPT;
        }
    
        return d;
    }     
    
    public DocFlavor forMimeTypeHOST(String flavor, String mimeType) {
        if (mimeType.equalsIgnoreCase("TEXT_HTML_HOST")) {
            if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
                d = DocFlavor.BYTE_ARRAY.TEXT_HTML_HOST;
            } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
                d = DocFlavor.INPUT_STREAM.TEXT_HTML_HOST;
            } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
                d = DocFlavor.URL.TEXT_HTML_HOST;
            }
        } else {
            if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
                d = DocFlavor.BYTE_ARRAY.TEXT_PLAIN_HOST;
            } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
                d = DocFlavor.INPUT_STREAM.TEXT_PLAIN_HOST;
            } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
                d = DocFlavor.URL.TEXT_PLAIN_HOST;
            }
        }
    
        return d;
    }
    
    public DocFlavor forMimeTypeUSASCII(String flavor, String mimeType) {
        if (mimeType.equalsIgnoreCase("TEXT_HTML_US_ASCII")) {
            if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
                d = DocFlavor.BYTE_ARRAY.TEXT_HTML_US_ASCII;
            } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
                d = DocFlavor.INPUT_STREAM.TEXT_HTML_US_ASCII;
            } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
                d = DocFlavor.URL.TEXT_HTML_US_ASCII;
            } 
        } else {
            if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
                d = DocFlavor.BYTE_ARRAY.TEXT_PLAIN_US_ASCII;
            } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
                d = DocFlavor.INPUT_STREAM.TEXT_PLAIN_US_ASCII;
            } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
                d = DocFlavor.URL.TEXT_PLAIN_US_ASCII;
            }
        }
    
        return d;
    }

    public DocFlavor forMimeTypeUTF16(String flavor, String mimeType) {
        if (mimeType.equalsIgnoreCase("TEXT_HTML_UTF_16")) {
            if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
                d = DocFlavor.BYTE_ARRAY.TEXT_HTML_UTF_16;
            } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
                d = DocFlavor.INPUT_STREAM.TEXT_HTML_UTF_16;
            } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
                d = DocFlavor.URL.TEXT_HTML_UTF_16;
            } 
        } else {
            if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
                d = DocFlavor.BYTE_ARRAY.TEXT_PLAIN_UTF_16;
            } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
                d = DocFlavor.INPUT_STREAM.TEXT_PLAIN_UTF_16;
            } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
                d = DocFlavor.URL.TEXT_PLAIN_UTF_16;
            }
        }
    
        return d;
    }
    public DocFlavor forMimeTypeUTF16LE(String flavor, String mimeType) {
        if (mimeType.equalsIgnoreCase("TEXT_HTML_UTF_16LE")) {
            if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
                d = DocFlavor.BYTE_ARRAY.TEXT_HTML_UTF_16LE;
            } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
                d = DocFlavor.INPUT_STREAM.TEXT_HTML_UTF_16LE;
            } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
                d = DocFlavor.URL.TEXT_HTML_UTF_16LE;
            } 
        } else {
            if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
                d = DocFlavor.BYTE_ARRAY.TEXT_PLAIN_UTF_16LE;
            } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
                d = DocFlavor.INPUT_STREAM.TEXT_PLAIN_UTF_16LE;
            } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
                d = DocFlavor.URL.TEXT_PLAIN_UTF_16LE;
            }
        }
    
        return d;
    }    
    
    public DocFlavor forMimeTypeUTF16BE(String flavor, String mimeType) {
        if (mimeType.equalsIgnoreCase("TEXT_HTML_UTF_16BE")) {
            if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
                d = DocFlavor.BYTE_ARRAY.TEXT_HTML_UTF_16BE;
            } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
                d = DocFlavor.INPUT_STREAM.TEXT_HTML_UTF_16BE;
            } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
                d = DocFlavor.URL.TEXT_HTML_UTF_16BE;
            }
        } else {
            if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
                d = DocFlavor.BYTE_ARRAY.TEXT_PLAIN_UTF_16BE;
            } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
                d = DocFlavor.INPUT_STREAM.TEXT_PLAIN_UTF_16BE;
            } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
                d = DocFlavor.URL.TEXT_PLAIN_UTF_16BE;
            }            
        }
    
        return d;
    } 
    
    public DocFlavor forMimeTypeUTF8(String flavor, String mimeType) {
        if (mimeType.equalsIgnoreCase("TEXT_HTML_UTF_16BE")) {
            if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
                d = DocFlavor.BYTE_ARRAY.TEXT_HTML_UTF_8;
            } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
                d = DocFlavor.INPUT_STREAM.TEXT_HTML_UTF_8;
            } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
                d = DocFlavor.URL.TEXT_HTML_UTF_8;
            }
        } else {
            if (flavor.equalsIgnoreCase("DocFlavor.BYTE_ARRAY")) {
                d = DocFlavor.BYTE_ARRAY.TEXT_PLAIN_UTF_8;
            } else if (flavor.equalsIgnoreCase("DocFlavor.INPUT_STREAM")) {
                d = DocFlavor.INPUT_STREAM.TEXT_PLAIN_UTF_8;
            } else if (flavor.equalsIgnoreCase("DocFlavor.URL")) {
                d = DocFlavor.URL.TEXT_PLAIN_UTF_8;
            }
        }
    
        return d;
    }
    
    public DocFlavor forMimeTypeBasic(String flavor, String mimeType) {
        if (mimeType.equalsIgnoreCase("TEXT_HTML_UTF_16BE")) {
            if (flavor.equalsIgnoreCase("DocFlavor.CHAR_ARRAY")) {
                d = DocFlavor.CHAR_ARRAY.TEXT_HTML;
            } else if (flavor.equalsIgnoreCase("DocFlavor.READER")) {
                d = DocFlavor.READER.TEXT_HTML;
            } else if (flavor.equalsIgnoreCase("DocFlavor.STRING")) {
                d = DocFlavor.STRING.TEXT_HTML;
            }
        } else {
            if (flavor.equalsIgnoreCase("DocFlavor.CHAR_ARRAY")) {
                d = DocFlavor.CHAR_ARRAY.TEXT_PLAIN;
            } else if (flavor.equalsIgnoreCase("DocFlavor.READER")) {
                d = DocFlavor.READER.TEXT_PLAIN;
            } else if (flavor.equalsIgnoreCase("DocFlavor.STRING")) {
                d = DocFlavor.STRING.TEXT_PLAIN;
            }
        }
    
        return d;
    }
    
    public DocFlavor forMimeTypePAGEABLE(String flavor) {
        return d = DocFlavor.SERVICE_FORMATTED.PAGEABLE; 
    }
    
    public DocFlavor forMimeTypePRINTABLE(String flavor) {
        return d = DocFlavor.SERVICE_FORMATTED.PRINTABLE;
    }

    public DocFlavor forMimeTypeRENDERABLEIMAGE(String flavor) {
        return d = DocFlavor.SERVICE_FORMATTED.RENDERABLE_IMAGE;
    }
}
