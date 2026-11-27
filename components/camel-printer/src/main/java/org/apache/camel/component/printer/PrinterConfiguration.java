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
package org.apache.camel.component.printer;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.print.DocFlavor;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.Sides;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

@UriParams
public class PrinterConfiguration {

    private static final List<String> FLAVORS = List.of(
            "DocFlavor.BYTE_ARRAY", "DocFlavor.INPUT_STREAM", "DocFlavor.URL", "DocFlavor.CHAR_ARRAY", "DocFlavor.READER",
            "DocFlavor.STRING");
    private static final String MIME_TYPES = "AUTOSENSE, GIF, JPEG, PDF, PCL, POSTSCRIPT, TEXT_HTML_HOST, TEXT_HTML_US_ASCII,"
                                             + " TEXT_HTML_UTF_16, TEXT_HTML_UTF_16LE, TEXT_HTML_UTF_16BE, TEXT_HTML_UTF_8,"
                                             + " TEXT_PLAIN_HOST, TEXT_PLAIN_US_ASCII, TEXT_PLAIN_UTF_16, TEXT_PLAIN_UTF_16LE,"
                                             + " TEXT_PLAIN_UTF_16BE, TEXT_PLAIN_UTF_8, TEXT_HTML, TEXT_PLAIN, PAGEABLE,"
                                             + " PRINTABLE, RENDERABLE_IMAGE";
    private URI uri;
    private MediaSizeName mediaSizeName;
    private Sides internalSides;
    private OrientationRequested internalOrientation;

    @UriPath
    @Metadata(required = true)
    private String hostname;
    @UriPath
    private int port;
    @UriPath
    private String printername;
    @UriParam
    private String printerPrefix;
    @UriParam(defaultValue = "1")
    private int copies = 1;
    @UriParam(defaultValue = "DocFlavor.BYTE_ARRAY")
    private String flavor = "DocFlavor.BYTE_ARRAY";
    @UriParam
    private DocFlavor docFlavor;
    @UriParam(defaultValue = "AUTOSENSE")
    private String mimeType = "AUTOSENSE";
    @UriParam(defaultValue = "na-letter")
    private String mediaSize;
    @UriParam(defaultValue = "one-sided", enums = "one-sided,duplex,tumble,two-sided-short-edge,two-sided-long-edge")
    private String sides;
    @UriParam(defaultValue = "portrait", enums = "portrait,landscape,reverse-portrait,reverse-landscape")
    private String orientation;
    @UriParam(defaultValue = "true")
    private boolean sendToPrinter = true;
    @UriParam
    private String mediaTray;

    public PrinterConfiguration() {
    }

    public PrinterConfiguration(URI uri) {
        this.uri = uri;
    }

    public void parseURI(URI uri) throws Exception {
        String protocol = uri.getScheme();

        if (!protocol.equalsIgnoreCase("lpr")) {
            throw new IllegalArgumentException("Unrecognized Print protocol: " + protocol + " for uri: " + uri);
        }

        setUri(uri);
        setHostname(uri.getHost());
        setPort(uri.getPort());

        // use path as printer name, but without any leading slashes
        String path = uri.getPath();
        path = StringHelper.removeStartingCharacters(path, '/');
        path = StringHelper.removeStartingCharacters(path, '\\');
        setPrintername(path);

        Map<String, Object> printSettings = URISupport.parseParameters(uri);
        // keep the defaults when not set, as the producer reads the mime type for the file of sendToPrinter=false
        if (printSettings.containsKey("flavor")) {
            setFlavor((String) printSettings.get("flavor"));
        }
        if (printSettings.containsKey("mimeType")) {
            setMimeType((String) printSettings.get("mimeType"));
        }
        setDocFlavor(assignDocFlavor(flavor, mimeType));

        setPrinterPrefix((String) printSettings.get("printerPrefix"));

        if (printSettings.containsKey("copies")) {
            setCopies(Integer.valueOf((String) printSettings.get("copies")));
        }
        setMediaSize((String) printSettings.get("mediaSize"));
        setSides((String) printSettings.get("sides"));
        setOrientation((String) printSettings.get("orientation"));
        setMediaSizeName(assignMediaSize(mediaSize));
        setInternalSides(assignSides(sides));
        setInternalOrientation(assignOrientation(orientation));
        if (printSettings.containsKey("sendToPrinter")) {
            if (!(Boolean.parseBoolean((String) printSettings.get("sendToPrinter")))) {
                setSendToPrinter(false);
            }
        }

        if (printSettings.containsKey("mediaTray")) {
            setMediaTray((String) printSettings.get("mediaTray"));
        }
    }

    private DocFlavor assignDocFlavor(String flavor, String mimeType) {
        // defaults
        if (mimeType == null) {
            mimeType = "AUTOSENSE";
        }
        if (flavor == null) {
            flavor = "DocFlavor.BYTE_ARRAY";
        }

        if (FLAVORS.stream().noneMatch(flavor::equalsIgnoreCase)) {
            throw new IllegalArgumentException(
                    "Unknown flavor: " + flavor + ". Use one of " + String.join(", ", FLAVORS) + ".");
        }

        DocFlavor d;
        DocFlavorAssigner docFlavorAssigner = new DocFlavorAssigner();
        if (mimeType.equalsIgnoreCase("AUTOSENSE")) {
            d = docFlavorAssigner.forMimeTypeAUTOSENSE(flavor);
        } else if (mimeType.equalsIgnoreCase("GIF")) {
            d = docFlavorAssigner.forMimeTypeGIF(flavor);
        } else if (mimeType.equalsIgnoreCase("JPEG")) {
            d = docFlavorAssigner.forMimeTypeJPEG(flavor);
        } else if (mimeType.equalsIgnoreCase("PDF")) {
            d = docFlavorAssigner.forMimeTypePDF(flavor);
        } else if (mimeType.equalsIgnoreCase("PCL")) {
            d = docFlavorAssigner.forMimeTypePCL(flavor);
        } else if (mimeType.equalsIgnoreCase("POSTSCRIPT")) {
            d = docFlavorAssigner.forMimeTypePOSTSCRIPT(flavor);
        } else if (mimeType.equalsIgnoreCase("TEXT_HTML_HOST")) {
            d = docFlavorAssigner.forMimeTypeHOST(flavor, mimeType);
        } else if (mimeType.equalsIgnoreCase("TEXT_HTML_US_ASCII")) {
            d = docFlavorAssigner.forMimeTypeUSASCII(flavor, mimeType);
        } else if (mimeType.equalsIgnoreCase("TEXT_HTML_UTF_16")) {
            d = docFlavorAssigner.forMimeTypeUTF16(flavor, mimeType);
        } else if (mimeType.equalsIgnoreCase("TEXT_HTML_UTF_16LE")) {
            d = docFlavorAssigner.forMimeTypeUTF16LE(flavor, mimeType);
        } else if (mimeType.equalsIgnoreCase("TEXT_HTML_UTF_16BE")) {
            d = docFlavorAssigner.forMimeTypeUTF16BE(flavor, mimeType);
        } else if (mimeType.equalsIgnoreCase("TEXT_HTML_UTF_8")) {
            d = docFlavorAssigner.forMimeTypeUTF8(flavor, mimeType);
        } else if (mimeType.equalsIgnoreCase("TEXT_PLAIN_HOST")) {
            d = docFlavorAssigner.forMimeTypeHOST(flavor, mimeType);
        } else if (mimeType.equalsIgnoreCase("TEXT_PLAIN_US_ASCII")) {
            d = docFlavorAssigner.forMimeTypeUSASCII(flavor, mimeType);
        } else if (mimeType.equalsIgnoreCase("TEXT_PLAIN_UTF_16")) {
            d = docFlavorAssigner.forMimeTypeUTF16(flavor, mimeType);
        } else if (mimeType.equalsIgnoreCase("TEXT_PLAIN_UTF_16LE")) {
            d = docFlavorAssigner.forMimeTypeUTF16LE(flavor, mimeType);
        } else if (mimeType.equalsIgnoreCase("TEXT_PLAIN_UTF_16BE")) {
            d = docFlavorAssigner.forMimeTypeUTF16BE(flavor, mimeType);
        } else if (mimeType.equalsIgnoreCase("TEXT_PLAIN_UTF_8")) {
            d = docFlavorAssigner.forMimeTypeUTF8(flavor, mimeType);
        } else if (mimeType.equalsIgnoreCase("TEXT_HTML")) {
            d = docFlavorAssigner.forMimeTypeBasic(flavor, mimeType);
        } else if (mimeType.equalsIgnoreCase("TEXT_PLAIN")) {
            d = docFlavorAssigner.forMimeTypeBasic(flavor, mimeType);
        } else if (mimeType.equalsIgnoreCase("PAGEABLE")) {
            d = docFlavorAssigner.forMimeTypePAGEABLE();
        } else if (mimeType.equalsIgnoreCase("PRINTABLE")) {
            d = docFlavorAssigner.forMimeTypePRINTABLE();
        } else if (mimeType.equalsIgnoreCase("RENDERABLE_IMAGE")) {
            d = docFlavorAssigner.forMimeTypeRENDERABLEIMAGE();
        } else {
            throw new IllegalArgumentException("Unknown mimeType: " + mimeType + ". Use one of " + MIME_TYPES + ".");
        }
        if (d == null) {
            // fail rather than printing with a doc flavor the user did not ask for
            throw new IllegalArgumentException(
                    "The flavor " + flavor + " has no doc flavor for the mimeType " + mimeType
                                               + ". TEXT_HTML and TEXT_PLAIN need DocFlavor.CHAR_ARRAY, DocFlavor.READER or"
                                               + " DocFlavor.STRING, and the other mime types DocFlavor.BYTE_ARRAY,"
                                               + " DocFlavor.INPUT_STREAM or DocFlavor.URL.");
        }

        return d;
    }

    private MediaSizeName assignMediaSize(String size) {
        if (size == null) {
            // default to NA letter if no size configured
            return MediaSizeName.NA_LETTER;
        }

        return new MediaSizeAssigner().selectMediaSizeName(size);
    }

    public Sides assignSides(String sidesString) {
        Sides answer;

        if (sidesString == null) {
            // default to one side if no slides configured
            answer = Sides.ONE_SIDED;
        } else if (sidesString.equalsIgnoreCase("one-sided")) {
            answer = Sides.ONE_SIDED;
        } else if (sidesString.equalsIgnoreCase("duplex")) {
            answer = Sides.DUPLEX;
        } else if (sidesString.equalsIgnoreCase("tumble")) {
            answer = Sides.TUMBLE;
        } else if (sidesString.equalsIgnoreCase("two-sided-short-edge")) {
            answer = Sides.TWO_SIDED_SHORT_EDGE;
        } else if (sidesString.equalsIgnoreCase("two-sided-long-edge")) {
            answer = Sides.TWO_SIDED_LONG_EDGE;
        } else {
            answer = Sides.ONE_SIDED;
        }

        return answer;
    }

    public OrientationRequested assignOrientation(final String orientation) {
        OrientationRequested answer;

        if (orientation == null) {
            // default to portrait
            answer = OrientationRequested.PORTRAIT;
        } else if (orientation.equalsIgnoreCase("portrait")) {
            answer = OrientationRequested.PORTRAIT;
        } else if (orientation.equalsIgnoreCase("landscape")) {
            answer = OrientationRequested.LANDSCAPE;
        } else if (orientation.equalsIgnoreCase("reverse-portrait")) {
            answer = OrientationRequested.REVERSE_PORTRAIT;
        } else if (orientation.equalsIgnoreCase("reverse-landscape")) {
            answer = OrientationRequested.REVERSE_LANDSCAPE;
        } else {
            answer = OrientationRequested.PORTRAIT;
        }

        return answer;
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public String getHostname() {
        return hostname;
    }

    /**
     * Hostname of the printer
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    /**
     * Port number of the printer
     */
    public void setPort(int port) {
        this.port = port;
    }

    public String getPrintername() {
        return printername;
    }

    /**
     * Name of the printer
     */
    public void setPrintername(String printername) {
        this.printername = printername;
    }

    public int getCopies() {
        return copies;
    }

    /**
     * Number of copies to print
     */
    public void setCopies(int copies) {
        this.copies = copies;
    }

    public String getFlavor() {
        return flavor;
    }

    /**
     * Sets DocFlavor to use.
     */
    public void setFlavor(String flavor) {
        this.flavor = flavor;
    }

    public DocFlavor getDocFlavor() {
        return docFlavor;
    }

    /**
     * Sets DocFlavor to use.
     */
    public void setDocFlavor(DocFlavor docFlavor) {
        this.docFlavor = docFlavor;
    }

    public String getMediaSize() {
        return mediaSize;
    }

    /**
     * Sets the stationary as defined by enumeration names in the javax.print.attribute.standard.MediaSizeName API. The
     * default setting is to use North American Letter sized stationary. The value's case is ignored, e.g. values of
     * iso_a4 and ISO_A4 may be used.
     */
    public void setMediaSize(String mediaSize) {
        this.mediaSize = mediaSize;
    }

    public String getSides() {
        return sides;
    }

    /**
     * Sets one sided or two sided printing based on the javax.print.attribute.standard.Sides API
     */
    public void setSides(String sides) {
        this.sides = sides;
    }

    public MediaSizeName getMediaSizeName() {
        return mediaSizeName;
    }

    public void setMediaSizeName(MediaSizeName mediaSizeName) {
        this.mediaSizeName = mediaSizeName;
    }

    public Sides getInternalSides() {
        return internalSides;
    }

    public void setInternalSides(Sides internalSides) {
        this.internalSides = internalSides;
    }

    public OrientationRequested getInternalOrientation() {
        return internalOrientation;
    }

    public void setInternalOrientation(OrientationRequested internalOrientation) {
        this.internalOrientation = internalOrientation;
    }

    public String getOrientation() {
        return orientation;
    }

    /**
     * Sets the page orientation.
     */
    public void setOrientation(String orientation) {
        this.orientation = orientation;
    }

    public String getMimeType() {
        return mimeType;
    }

    /**
     * Sets mimeTypes supported by the javax.print.DocFlavor API
     */
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public boolean isSendToPrinter() {
        return sendToPrinter;
    }

    /**
     * etting this option to false prevents sending of the print data to the printer
     */
    public void setSendToPrinter(boolean sendToPrinter) {
        this.sendToPrinter = sendToPrinter;
    }

    public String getMediaTray() {
        return mediaTray;
    }

    /**
     * Sets MediaTray supported by the javax.print.DocFlavor API, for example upper,middle etc.
     */
    public void setMediaTray(String mediaTray) {
        this.mediaTray = mediaTray;
    }

    public String getPrinterPrefix() {
        return printerPrefix;
    }

    /**
     * Sets the prefix name of the printer, it is useful when the printer name does not start with //hostname/printer
     */
    public void setPrinterPrefix(String printerPrefix) {
        this.printerPrefix = printerPrefix;
    }
}
