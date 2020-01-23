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

import java.io.InputStream;
import java.util.Locale;

import javax.print.DocFlavor;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaTray;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrinterProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(PrinterProducer.class);

    private final PrinterConfiguration config;
    private PrinterOperations printerOperations;
    private PrintService printService;
    private String printer;
    
    public PrinterProducer(Endpoint endpoint, PrinterConfiguration config) throws Exception {
        super(endpoint);
        this.config = config;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, body);
        String jobName = exchange.getIn().getHeader(PrinterEndpoint.JOB_NAME, "Camel: lpr", String.class);
        print(is, jobName);
    }
    
    private void print(InputStream body, String jobName) throws PrintException { 
        if (printerOperations.getPrintService().isDocFlavorSupported(printerOperations.getFlavor())) {
            PrintDocument printDoc = new PrintDocument(body, printerOperations.getFlavor());
            printerOperations.print(printDoc, config.isSendToPrinter(), config.getMimeType(), jobName); 
        }
    }

    private DocFlavor assignDocFlavor() throws Exception {
        return config.getDocFlavor();      
    }    

    private PrintRequestAttributeSet assignPrintAttributes() throws PrintException {
        PrintRequestAttributeSet printRequestAttributeSet = new HashPrintRequestAttributeSet();
        if (config.getCopies() >= 1) {
            printRequestAttributeSet.add(new Copies(config.getCopies()));
        } else {
            throw new PrintException("Number of print copies should be greater than zero");
        }
        printRequestAttributeSet.add(config.getMediaSizeName());
        printRequestAttributeSet.add(config.getInternalSides());
        printRequestAttributeSet.add(config.getInternalOrientation());

        if (config.getMediaTray() != null) {
            MediaTray mediaTray = resolveMediaTray(config.getMediaTray());
        
            if (mediaTray == null) {
                throw new PrintException("mediatray not found " + config.getMediaTray());
            }
            
            printRequestAttributeSet.add(mediaTray);
        }
        
        return printRequestAttributeSet;
    }
    
    private MediaTray resolveMediaTray(String tray) {
        Media medias[] = (Media[]) getPrintService().getSupportedAttributeValues(Media.class, null, null);
        
        if (medias == null) {
            return null;
        } else {
            for (Media media : medias) {
                if (media instanceof MediaTray) {
                    MediaTray mediaTray = (MediaTray) media;
                    String trayName = mediaTray.toString().trim();
                    if (trayName.contains(" ")) {
                        trayName = trayName.replace(' ', '_');
                    }
                    if (trayName.equals(tray)) {
                        return mediaTray;
                    }
                }
            }
            return null;
        }
    }
    
    private PrintService assignPrintService() throws PrintException {
        PrintService printService;
        
        if ((config.getHostname().equalsIgnoreCase("localhost")) 
            && (config.getPrintername().equalsIgnoreCase("default"))) {
            printService = PrintServiceLookup.lookupDefaultPrintService();            
        } else {
            PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
            String name;
            if (config.getHostname().equalsIgnoreCase("localhost")) {
                // no hostname for localhost printers
                name = config.getPrintername();
            } else {
                name = config.getHostname() + "/" + config.getPrintername();
                if (config.getPrinterPrefix() != null) {
                    name = config.getPrinterPrefix() + name;
                }
            }
            LOG.debug("Using printer name: {}", name);
            setPrinter(name);
            int position = findPrinter(services, printer);
            if (position < 0) {
                throw new PrintException("No printer found with name: " + printer + ". Please verify that the host and printer are registered and reachable from this machine.");
            }         
            printService = services[position];
        }
        return printService;
    }
    
    private int findPrinter(PrintService[] services, String printer) {
        int position = -1;
        // align slashes so we match / or \
        printer = printer.toLowerCase(Locale.US);
        printer = printer.replace('\\', '/');
        for (int i = 0; i < services.length; i++) {
            String printerName = services[i].getName();
            LOG.debug("Printer service printer name: {}", printerName);
            // align slashes so we match / or \
            printerName = printerName.toLowerCase(Locale.US);
            printerName = printerName.replace('\\', '/');
            if (printer.endsWith(printerName)) {
                position = i;
                break;
            }
        }
        return position;
    }

    public PrinterConfiguration getConfig() {
        return config;
    }

    public PrinterOperations getPrinterOperations() {
        return printerOperations;
    }

    public void setPrinterOperations(PrinterOperations printerOperations) {
        this.printerOperations = printerOperations;
    }

    public PrintService getPrintService() {
        return printService;
    }

    public void setPrintService(PrintService printService) {
        this.printService = printService;
    }

    public String getPrinter() {
        return printer;
    }

    public void setPrinter(String printer) {
        this.printer = printer;
    }

    @Override
    protected void doStart() throws Exception {
        if (printService ==  null) {
            printService = assignPrintService();
        }
        ObjectHelper.notNull(printService, "PrintService", this);

        if (printerOperations == null) {
            printerOperations = new PrinterOperations(printService, assignDocFlavor(), assignPrintAttributes());
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

}
