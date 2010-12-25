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

import java.io.InputStream;

import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class PrinterProducer extends DefaultProducer {
    private static final transient Log LOG = LogFactory.getLog(PrinterProducer.class);
    private final PrinterConfiguration config;
    private PrinterOperations printerOperations;
    private PrintService printService;
    private String printer;
    
    public PrinterProducer(Endpoint endpoint, PrinterConfiguration config) throws Exception {
        super(endpoint);
        this.config = config;
    }

    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, body);
        print(is);
    }
    
    private void print(InputStream body) throws PrintException { 
        if (printerOperations.getPrintService().isDocFlavorSupported(printerOperations.getFlavor())) {
            PrintDocument printDoc = new PrintDocument(body, printerOperations.getFlavor());        
            printerOperations.print(printDoc, config.getCopies(), config.isSendToPrinter(), config.getMimeType()); 
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
        
        return printRequestAttributeSet;
    }
    
    private DocPrintJob assignPrintJob(PrintService printService) {
        return printService.createPrintJob(); 
    }
    
    private PrintService assignPrintService() throws PrintException {
        PrintService printService;
        
        if ((config.getHostname().equalsIgnoreCase("localhost")) 
            && (config.getPrintername().equalsIgnoreCase("/default"))) {
            printService = PrintServiceLookup.lookupDefaultPrintService();            
        } else {
            PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
            setPrinter("\\\\" + config.getHostname() + "\\" + config.getPrintername());
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
        for (int i = 0; i < services.length; i++) {
            if (printer.equalsIgnoreCase(services[i].getName())) {
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
            printerOperations = new PrinterOperations(printService, assignPrintJob(printService), assignDocFlavor(), assignPrintAttributes());
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

}
