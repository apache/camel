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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.Sides;

import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrinterOperations implements PrinterOperationsInterface {
    private static final Logger LOG = LoggerFactory.getLogger(PrinterOperations.class);
    private PrintService printService;
    private DocFlavor flavor;
    private PrintRequestAttributeSet printRequestAttributeSet;
    private Doc doc;

    public PrinterOperations() throws PrintException {        
        printService = PrintServiceLookup.lookupDefaultPrintService();
        if (printService == null) {
            throw new PrintException("Printer lookup failure. No default printer set up for this host");
        }
        flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
        printRequestAttributeSet = new HashPrintRequestAttributeSet();
        printRequestAttributeSet.add(new Copies(1));
        printRequestAttributeSet.add(MediaSizeName.NA_LETTER);
        printRequestAttributeSet.add(Sides.ONE_SIDED);
    }

    public PrinterOperations(PrintService printService, DocFlavor flavor, PrintRequestAttributeSet printRequestAttributeSet) throws PrintException {
        this.setPrintService(printService);
        this.setFlavor(flavor);
        this.setPrintRequestAttributeSet(printRequestAttributeSet);
    }

    public void print(Doc doc, int copies, boolean sendToPrinter, String mimeType, String jobName) throws PrintException {
        LOG.trace("Print Service: " + this.printService.getName());
        LOG.trace("About to print " + copies + " copy(s)");
        
        for (int i = 0; i < copies; i++) {
            if (!sendToPrinter) {
                LOG.debug("Print flag is set to false. This job will not be printed until this setting remains in effect."
                        + " Please set the flag to true or remove the setting.");

                File file;
                if (mimeType.equalsIgnoreCase("GIF") || mimeType.equalsIgnoreCase("RENDERABLE_IMAGE")) {
                    file = new File("./target/TestPrintJobNo" + i + "_" + UUID.randomUUID() + ".gif");
                } else if (mimeType.equalsIgnoreCase("JPEG")) {
                    file = new File("./target/TestPrintJobNo" + i + "_" + UUID.randomUUID() + ".jpeg");
                } else if (mimeType.equalsIgnoreCase("PDF")) {
                    file = new File("./target/TestPrintJobNo" + i + "_" + UUID.randomUUID() + ".pdf");
                } else {
                    file = new File("./target/TestPrintJobNo" + i + "_" + UUID.randomUUID() + ".txt");
                }

                LOG.debug("Writing print job to file: " + file.getAbsolutePath());
                try {
                    InputStream in = doc.getStreamForBytes();
                    FileOutputStream fos = new FileOutputStream(file);
                    IOHelper.copyAndCloseInput(in, fos);
                    IOHelper.close(fos);
                } catch (Exception e) {
                    throw new PrintException("Error writing Document to the target file " + file.getAbsolutePath());
                }    
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Issuing Job {} to Printer: {}", i, this.printService.getName());
                }
                print(doc, jobName);
            }
        }
    }
        
    public void print(Doc doc, String jobName) throws PrintException {
        // we need create a new job for each print 
        DocPrintJob job = getPrintService().createPrintJob();
        PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet(printRequestAttributeSet);
        attrs.add(new JobName(jobName, Locale.getDefault()));
        job.print(doc, attrs);
    }

    public PrintService getPrintService() {
        return printService;
    }

    public void setPrintService(PrintService printService) {
        this.printService = printService;
    }
    
    public DocFlavor getFlavor() {
        return flavor;
    }

    public void setFlavor(DocFlavor flavor) {
        this.flavor = flavor;
    }

    public PrintRequestAttributeSet getPrintRequestAttributeSet() {
        return printRequestAttributeSet;
    }

    public void setPrintRequestAttributeSet(PrintRequestAttributeSet printRequestAttributeSet) {
        this.printRequestAttributeSet = printRequestAttributeSet;
    }

    public Doc getDoc() {
        return doc;
    }

    public void setDoc(Doc doc) {
        this.doc = doc;
    }

}
