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
    private URI uri;
    private MediaSizeName mediaSizeName;
    private Sides internalSides;
    private OrientationRequested internalOrientation;

    @UriPath(description = "The host name of the printer. With localhost, the producer looks up a printer of this machine "
                           + "by printername alone, or uses the default printer of this machine when printername is default. "
                           + "With another host, it looks up the printer by hostname/printername.")
    @Metadata(required = true)
    private String hostname;
    @UriPath(description = "The port of the printer. The producer does not use it, as it looks up the printer by name. It "
                           + "is only part of the service location of the endpoint.")
    private int port;
    @UriPath(description = "The name of the printer, for example default or sales/salesprinter, as the print services of "
                           + "this machine (javax.print.PrintServiceLookup) know it, so a remote printer must be installed on "
                           + "this machine. A print service matches when its name ends with the looked-up name or the other "
                           + "way round, ignoring case and treating / and \\ alike. The producer fails to start when no print "
                           + "service matches.")
    private String printername;
    @UriParam(description = "A text that the producer puts before hostname/printername to form the name of the printer to "
                            + "look up. It is not used when the host is localhost.")
    private String printerPrefix;
    @UriParam(defaultValue = "1",
              description = "The number of copies to print, sent as the Copies attribute of the print request. The producer "
                            + "fails to start when the value is less than 1.")
    private int copies = 1;
    @UriParam(description = "The representation class of the doc flavor, as the name of a nested class of "
                            + "javax.print.DocFlavor: DocFlavor.BYTE_ARRAY, DocFlavor.INPUT_STREAM or DocFlavor.URL, and for "
                            + "the mimeType TEXT_HTML or TEXT_PLAIN DocFlavor.CHAR_ARRAY, DocFlavor.READER or "
                            + "DocFlavor.STRING. If not set, DocFlavor.BYTE_ARRAY is used. Together with mimeType it selects "
                            + "the doc flavor, and a combination that the component does not know selects "
                            + "DocFlavor.BYTE_ARRAY.AUTOSENSE.")
    private String flavor;
    @UriParam(description = "The doc flavor as a javax.print.DocFlavor, for example a reference to a bean. When set, it "
                            + "replaces the doc flavor that the flavor and mimeType options select.")
    private DocFlavor docFlavor;
    @UriParam(description = "The format of the document, as the name of a doc flavor constant of javax.print.DocFlavor, for "
                            + "example AUTOSENSE, PDF, POSTSCRIPT, PCL, GIF, JPEG, TEXT_PLAIN_UTF_8 or TEXT_HTML. If not set, "
                            + "AUTOSENSE is used, which lets the print service detect the format. With sendToPrinter set to "
                            + "false, it also selects the extension of the written file.")
    private String mimeType;
    @UriParam(defaultValue = "na-letter",
              description = "The paper size, as the name that javax.print.attribute.standard.MediaSizeName gives it, for "
                            + "example na-letter, na-legal, iso-a4 or japanese-postcard. Case is ignored and _ may be used for "
                            + "-, so ISO_A4 works too. An unknown size fails the creation of the endpoint.")
    private String mediaSize;
    @UriParam(defaultValue = "one-sided", enums = "one-sided,duplex,tumble,two-sided-short-edge,two-sided-long-edge",
              description = "Whether to print on one side or on both sides of the paper, as a value of "
                            + "javax.print.attribute.standard.Sides. The value duplex is the same as two-sided-long-edge, and "
                            + "tumble the same as two-sided-short-edge.")
    private String sides;
    @UriParam(defaultValue = "portrait", enums = "portrait,landscape,reverse-portrait,reverse-landscape",
              description = "The orientation of the printed pages, as a value of "
                            + "javax.print.attribute.standard.OrientationRequested.")
    private String orientation;
    @UriParam(defaultValue = "true",
              description = "Whether to send the print job to the printer. If false, the producer writes the document to a "
                            + "file named PrintOutput_ followed by a random UUID in the target directory of the working "
                            + "directory, with the extension gif, jpeg, pdf or txt that matches mimeType. The producer still "
                            + "looks up the printer when it starts, and the printer must support the doc flavor.")
    private boolean sendToPrinter = true;
    @UriParam(description = "The name of the input tray to take the paper from, as javax.print.attribute.standard.MediaTray "
                            + "gives it with spaces replaced by _, for example top, middle, bottom or manual. The name must "
                            + "match, with the same case, a tray that the printer reports, or the producer fails to start.")
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
        setFlavor((String) printSettings.get("flavor"));
        setMimeType((String) printSettings.get("mimeType"));
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

        DocFlavor d = DocFlavor.BYTE_ARRAY.AUTOSENSE;
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

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPrintername() {
        return printername;
    }

    public void setPrintername(String printername) {
        this.printername = printername;
    }

    public int getCopies() {
        return copies;
    }

    public void setCopies(int copies) {
        this.copies = copies;
    }

    public String getFlavor() {
        return flavor;
    }

    public void setFlavor(String flavor) {
        this.flavor = flavor;
    }

    public DocFlavor getDocFlavor() {
        return docFlavor;
    }

    public void setDocFlavor(DocFlavor docFlavor) {
        this.docFlavor = docFlavor;
    }

    public String getMediaSize() {
        return mediaSize;
    }

    public void setMediaSize(String mediaSize) {
        this.mediaSize = mediaSize;
    }

    public String getSides() {
        return sides;
    }

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

    public void setOrientation(String orientation) {
        this.orientation = orientation;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public boolean isSendToPrinter() {
        return sendToPrinter;
    }

    public void setSendToPrinter(boolean sendToPrinter) {
        this.sendToPrinter = sendToPrinter;
    }

    public String getMediaTray() {
        return mediaTray;
    }

    public void setMediaTray(String mediaTray) {
        this.mediaTray = mediaTray;
    }

    public String getPrinterPrefix() {
        return printerPrefix;
    }

    public void setPrinterPrefix(String printerPrefix) {
        this.printerPrefix = printerPrefix;
    }
}
