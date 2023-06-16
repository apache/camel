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
package org.apache.camel.component.file.azure;

import java.net.URI;

import org.apache.camel.component.file.remote.RemoteFileConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class FilesConfiguration extends RemoteFileConfiguration {

    public static final int DEFAULT_HTTPS_PORT = 443;
    public static final String DEFAULT_INTERNET_DOMAIN = "file.core.windows.net";

    @UriParam(label = "both", description = "part of SAS token", secret = true)
    protected String sv;
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    protected String ss;
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    protected String srt;
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    protected String sp;
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    protected String se;
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    protected String st;
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    protected String spr;
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    protected String sig;

    @UriParam(label = "both", description = "part of SAS token", secret = true)
    private String si;  // service SAS only
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    private String sr;  // service SAS only
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    private String sdd; // service SAS only
    @UriParam(label = "both", description = "part of SAS token", secret = true)
    private String sip;

    @UriParam(label = "both", description = "Shared key (storage account key)", secret = true)
    private String sharedKey;

    private FilesToken token = new FilesToken();

    private String account;
    private String share;

    public FilesConfiguration() {
        setProtocol(FilesComponent.SCHEME);
    }

    public FilesConfiguration(URI uri) {
        super(uri);
        setSendNoop(false);
        setBinary(true);
        setPassiveMode(true);
    }

    @Override
    protected void setDefaultPort() {
        setPort(DEFAULT_HTTPS_PORT);
    }

    @Override
    public void setDirectory(String path) {
        // split URI path to share and starting directory
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Illegal share[/dir]: " + path);
        }
        var dir = "";
        var separator = path.indexOf(FilesPath.PATH_SEPARATOR);
        if (separator != -1) {
            dir = path.substring(separator);
            share = path.substring(0, separator);
        }
        super.setDirectory(dir);
    }

    public String getShare() {
        return share;
    }

    @Override
    public String remoteServerInformation() {
        return getProtocol() + "://" + getAccount();
    }

    /**
     * Files service account or &lt;account>.file.core.windows.net hostname.
     */
    @Override
    public void setHost(String accountOrHostname) {
        var dot = accountOrHostname.indexOf('.');
        var hasDot = dot >= 0;
        account = hasDot ? accountOrHostname.substring(0, dot) : accountOrHostname;
        super.setHost(hasDot ? accountOrHostname : account + '.' + DEFAULT_INTERNET_DOMAIN);
    }

    public String getAccount() {
        return account;
    }

    public String getSv() {
        return sv;
    }

    public void setSv(String sv) {
        token.setSv(sv);
        this.sv = sv;
    }

    public String getSs() {
        return ss;
    }

    public void setSs(String ss) {
        token.setSs(ss);
        this.ss = ss;
    }

    public String getSrt() {
        return srt;
    }

    public void setSrt(String srt) {
        token.setSrt(srt);
        this.srt = srt;
    }

    public String getSp() {
        return sp;
    }

    public void setSp(String sp) {
        token.setSp(sp);
        this.sp = sp;
    }

    public String getSe() {
        return se;
    }

    public void setSe(String se) {
        token.setSe(se);
        this.se = se;
    }

    public String getSt() {
        return st;
    }

    public void setSt(String st) {
        token.setSt(st);
        this.st = st;
    }

    public String getSpr() {
        return spr;
    }

    public void setSpr(String spr) {
        token.setSpr(spr);
        this.spr = spr;
    }

    public String getSig() {
        return sig;
    }

    public void setSig(String sig) {
        token.setSig(sig);
        this.sig = sig;
    }

    public String getSi() {
        return si;
    }

    public void setSi(String si) {
        token.setSi(si);
        this.si = si;
    }

    public String getSr() {
        return sr;
    }

    public void setSr(String sr) {
        token.setSr(sr);
        this.sr = sr;
    }

    public String getSdd() {
        return sdd;
    }

    public void setSdd(String sdd) {
        token.setSdd(sdd);
        this.sdd = sdd;
    }

    public String getSip() {
        return sip;
    }

    public void setSip(String sip) {
        token.setSip(sip);
        this.sip = sip;
    }

    public String getSharedKey() {
        return sharedKey;
    }

    public void setSharedKey(String sharedKey) {
        this.sharedKey = sharedKey;
    }

    public FilesToken getToken() {
        return token;
    }

}
