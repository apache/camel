/**
 * Dual licensed under the Apache License 2.0 and the MIT license.
 * $Revision$ $Date: 2009-05-10 13:06:45 +1000 (Sun, 10 May 2009) $
 */

dojo.provide('dojox.cometd');
dojo.registerModulePath('org','../org');
dojo.require('org.cometd');
dojo.require('dojo.io.script');

// Remap cometd JSON functions to dojo JSON functions
org.cometd.JSON.toJSON = dojo.toJson;
org.cometd.JSON.fromJSON = dojo.fromJson;

// The default cometd instance
dojox.cometd = new org.cometd.Cometd();

// Remap toolkit-specific transport calls
dojox.cometd.LongPollingTransport = function()
{
    this.xhrSend = function(packet)
    {
        var deferred = dojo.rawXhrPost({
            url: packet.url,
            sync: packet.sync === true,
            contentType: 'application/json;charset=UTF-8',
            headers: packet.headers,
            postData: packet.body,
            handleAs: 'json',
            load: packet.onSuccess,
            error: function(error)
            {
                packet.onError(error.message, deferred ? deferred.ioArgs.error : error);
            }
        });
        return deferred.ioArgs.xhr;
    };
};
dojox.cometd.LongPollingTransport.prototype = new org.cometd.LongPollingTransport();
dojox.cometd.LongPollingTransport.prototype.constructor = dojox.cometd.LongPollingTransport;

dojox.cometd.CallbackPollingTransport = function()
{
    this.jsonpSend = function(packet)
    {
        var deferred = dojo.io.script.get({
            url: packet.url,
            sync: packet.sync === true,
            callbackParamName: 'jsonp',
            content: {
                // In callback-polling, the content must be sent via the 'message' parameter
                message: packet.body
            },
            load: packet.onSuccess,
            error: function(error)
            {
                packet.onError(error.message, deferred ? deferred.ioArgs.error : error);
            }
        });
        return undefined;
    };
};
dojox.cometd.CallbackPollingTransport.prototype = new org.cometd.CallbackPollingTransport();
dojox.cometd.CallbackPollingTransport.prototype.constructor = dojox.cometd.CallbackPollingTransport;

dojox.cometd.registerTransport('long-polling', new dojox.cometd.LongPollingTransport());
dojox.cometd.registerTransport('callback-polling', new dojox.cometd.CallbackPollingTransport());

// Create a compatibility API for dojox.cometd instance with
// the original API.

dojox.cometd._init = dojox.cometd.init;

dojox.cometd._unsubscribe = dojox.cometd.unsubscribe;

dojox.cometd.unsubscribe = function(channelOrToken, objOrFunc, funcName)
{
    if (typeof channelOrToken === 'string')
    {
        throw "Deprecated function unsubscribe(string). Use unsubscribe(object) passing as argument the return value of subscribe()";
    }

    dojox.cometd._unsubscribe(channelOrToken);
};

dojox.cometd._metaHandshakeEvent = function(event)
{
    event.action = "handshake";
    dojo.publish("/cometd/meta", [event]);
};

dojox.cometd._metaConnectEvent = function(event)
{
    event.action = "connect";
    dojo.publish("/cometd/meta", [event]);
};

dojox.cometd.addListener('/meta/handshake', dojox.cometd, dojox.cometd._metaHandshakeEvent);
dojox.cometd.addListener('/meta/connect', dojox.cometd, dojox.cometd._metaConnectEvent);
