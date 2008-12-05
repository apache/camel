dojo.provide("dojox.cometd.timesync");
dojo.require("dojox.cometd");

dojox.cometd.timesync= new function(){

        this._lastOut=new Date().getTime();
        this._lastIn=this._lastOut;
        this._lastInterval=-1;
        this._lastPoll=-1;

        this._in=function(msg){
                var channel=msg.channel;
                if (channel=="/meta/handshake" || channel=="/meta/connect"){
                        this._lastIn=new Date().getTime();
                        this._lastPoll=this._lastIn-this._lastOut;
                }
                return msg;
        }

        this._out=function(msg){
                var channel=msg.channel;
                if (channel=="/meta/handshake" || channel=="/meta/connect"){
                        this._lastOut=new Date().getTime();
                        this._lastInterval=(this._lastOut-this._lastIn);
                        
                        if (!msg.ext)
                                msg.ext={};
                        msg.ext.timesync={
                                ts: this._lastOut,
                                i: this._lastInterval
                        };
                        
                }
                return msg;
        }
};

dojox.cometd._extendInList.push(dojo.hitch(dojox.cometd.timesync,"_in"));
dojox.cometd._extendOutList.push(dojo.hitch(dojox.cometd.timesync,"_out"));