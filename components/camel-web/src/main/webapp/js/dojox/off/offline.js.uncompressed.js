/*
	Copyright (c) 2004-2009, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/

/*
	This is a compiled version of Dojo, built for deployment and not for
	development. To get an editable version, please visit:

		http://dojotoolkit.org

	for documentation and information on getting the source.
*/

if(!dojo._hasResource["dojox.storage.Provider"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.storage.Provider"] = true;
dojo.provide("dojox.storage.Provider");

dojo.declare("dojox.storage.Provider", null, {
	// summary: A singleton for working with dojox.storage.
	// description:
	//		dojox.storage exposes the current available storage provider on this
	//		platform. It gives you methods such as dojox.storage.put(),
	//		dojox.storage.get(), etc.
	//		
	//		For more details on dojox.storage, see the primary documentation
	//		page at
	//			http://manual.dojotoolkit.org/storage.html
	//		
	//		Note for storage provider developers who are creating subclasses-
	//		This is the base class for all storage providers Specific kinds of
	//		Storage Providers should subclass this and implement these methods.
	//		You should avoid initialization in storage provider subclass's
	//		constructor; instead, perform initialization in your initialize()
	//		method. 
	constructor: function(){
	},
	
	// SUCCESS: String
	//	Flag that indicates a put() call to a 
	//	storage provider was succesful.
	SUCCESS: "success",
	
	// FAILED: String
	//	Flag that indicates a put() call to 
	//	a storage provider failed.
	FAILED: "failed",
	
	// PENDING: String
	//	Flag that indicates a put() call to a 
	//	storage provider is pending user approval.
	PENDING: "pending",
	
	// SIZE_NOT_AVAILABLE: String
	//	Returned by getMaximumSize() if this storage provider can not determine
	//	the maximum amount of data it can support. 
	SIZE_NOT_AVAILABLE: "Size not available",
	
	// SIZE_NO_LIMIT: String
	//	Returned by getMaximumSize() if this storage provider has no theoretical
	//	limit on the amount of data it can store. 
	SIZE_NO_LIMIT: "No size limit",

	// DEFAULT_NAMESPACE: String
	//	The namespace for all storage operations. This is useful if several
	//	applications want access to the storage system from the same domain but
	//	want different storage silos. 
	DEFAULT_NAMESPACE: "default",
	
	// onHideSettingsUI: Function
	//	If a function is assigned to this property, then when the settings
	//	provider's UI is closed this function is called. Useful, for example,
	//	if the user has just cleared out all storage for this provider using
	//	the settings UI, and you want to update your UI.
	onHideSettingsUI: null,

	initialize: function(){
		// summary: 
		//		Allows this storage provider to initialize itself. This is
		//		called after the page has finished loading, so you can not do
		//		document.writes(). Storage Provider subclasses should initialize
		//		themselves inside of here rather than in their function
		//		constructor.
		console.warn("dojox.storage.initialize not implemented");
	},
	
	isAvailable: function(){ /*Boolean*/
		// summary: 
		//		Returns whether this storage provider is available on this
		//		platform. 
		console.warn("dojox.storage.isAvailable not implemented");
	},

	put: function(	/*string*/ key,
					/*object*/ value, 
					/*function*/ resultsHandler,
					/*string?*/ namespace){
		// summary:
		//		Puts a key and value into this storage system.
		// description:
		//		Example-
		//			var resultsHandler = function(status, key, message, namespace){
		//			  alert("status="+status+", key="+key+", message="+message);
		//			};
		//			dojox.storage.put("test", "hello world", resultsHandler);
		//
		//			Arguments:
		//
		//			status - The status of the put operation, given by
		//								dojox.storage.FAILED, dojox.storage.SUCCEEDED, or
		//								dojox.storage.PENDING
		//			key - The key that was used for the put
		//			message - An optional message if there was an error or things failed.
		//			namespace - The namespace of the key. This comes at the end since
		//									it was added later.
		//	
		//		Important note: if you are using Dojo Storage in conjunction with
		//		Dojo Offline, then you don't need to provide
		//		a resultsHandler; this is because for Dojo Offline we 
		//		use Google Gears to persist data, which has unlimited data
		//		once the user has given permission. If you are using Dojo
		//		Storage apart from Dojo Offline, then under the covers hidden
		//		Flash might be used, which is both asychronous and which might
		//		get denied; in this case you must provide a resultsHandler.
		// key:
		//		A string key to use when retrieving this value in the future.
		// value:
		//		A value to store; this can be any JavaScript type.
		// resultsHandler:
		//		A callback function that will receive three arguments. The
		//		first argument is one of three values: dojox.storage.SUCCESS,
		//		dojox.storage.FAILED, or dojox.storage.PENDING; these values
		//		determine how the put request went. In some storage systems
		//		users can deny a storage request, resulting in a
		//		dojox.storage.FAILED, while in other storage systems a storage
		//		request must wait for user approval, resulting in a
		//		dojox.storage.PENDING status until the request is either
		//		approved or denied, resulting in another call back with
		//		dojox.storage.SUCCESS. 
		//		The second argument in the call back is the key name that was being stored.
		//		The third argument in the call back is an optional message that
		//		details possible error messages that might have occurred during
		//		the storage process.
		//	namespace:
		//		Optional string namespace that this value will be placed into;
		//		if left off, the value will be placed into dojox.storage.DEFAULT_NAMESPACE
		
		console.warn("dojox.storage.put not implemented");
	},

	get: function(/*string*/ key, /*string?*/ namespace){ /*Object*/
		// summary:
		//		Gets the value with the given key. Returns null if this key is
		//		not in the storage system.
		// key:
		//		A string key to get the value of.
		//	namespace:
		//		Optional string namespace that this value will be retrieved from;
		//		if left off, the value will be retrieved from dojox.storage.DEFAULT_NAMESPACE
		// return: Returns any JavaScript object type; null if the key is not present
		console.warn("dojox.storage.get not implemented");
	},

	hasKey: function(/*string*/ key, /*string?*/ namespace){
		// summary: Determines whether the storage has the given key. 
		return !!this.get(key, namespace); // Boolean
	},

	getKeys: function(/*string?*/ namespace){ /*Array*/
		// summary: Enumerates all of the available keys in this storage system.
		// return: Array of available keys
		console.warn("dojox.storage.getKeys not implemented");
	},
	
	clear: function(/*string?*/ namespace){
		// summary: 
		//		Completely clears this storage system of all of it's values and
		//		keys. If 'namespace' is provided just clears the keys in that
		//		namespace.
		console.warn("dojox.storage.clear not implemented");
	},
  
	remove: function(/*string*/ key, /*string?*/ namespace){
		// summary: Removes the given key from this storage system.
		console.warn("dojox.storage.remove not implemented");
	},
	
	getNamespaces: function(){ /*string[]*/
		console.warn("dojox.storage.getNamespaces not implemented");
	},

	isPermanent: function(){ /*Boolean*/
		// summary:
		//		Returns whether this storage provider's values are persisted
		//		when this platform is shutdown. 
		console.warn("dojox.storage.isPermanent not implemented");
	},

	getMaximumSize: function(){ /* mixed */
		// summary: The maximum storage allowed by this provider
		// returns: 
		//	Returns the maximum storage size 
		//	supported by this provider, in 
		//	thousands of bytes (i.e., if it 
		//	returns 60 then this means that 60K 
		//	of storage is supported).
		//
		//	If this provider can not determine 
		//	it's maximum size, then 
		//	dojox.storage.SIZE_NOT_AVAILABLE is 
		//	returned; if there is no theoretical
		//	limit on the amount of storage 
		//	this provider can return, then
		//	dojox.storage.SIZE_NO_LIMIT is 
		//	returned
		console.warn("dojox.storage.getMaximumSize not implemented");
	},
		
	putMultiple: function(	/*array*/ keys,
							/*array*/ values, 
							/*function*/ resultsHandler,
							/*string?*/ namespace){
		// summary:
		//		Puts multiple keys and values into this storage system.
		// description:
		//		Example-
		//			var resultsHandler = function(status, key, message){
		//			  alert("status="+status+", key="+key+", message="+message);
		//			};
		//			dojox.storage.put(["test"], ["hello world"], resultsHandler);
		//	
		//		Important note: if you are using Dojo Storage in conjunction with
		//		Dojo Offline, then you don't need to provide
		//		a resultsHandler; this is because for Dojo Offline we 
		//		use Google Gears to persist data, which has unlimited data
		//		once the user has given permission. If you are using Dojo
		//		Storage apart from Dojo Offline, then under the covers hidden
		//		Flash might be used, which is both asychronous and which might
		//		get denied; in this case you must provide a resultsHandler.
		// keys:
		//		An array of string keys to use when retrieving this value in the future,
		//		one per value to be stored
		// values:
		//		An array of values to store; this can be any JavaScript type, though the
		//		performance of plain strings is considerably better
		// resultsHandler:
		//		A callback function that will receive three arguments. The
		//		first argument is one of three values: dojox.storage.SUCCESS,
		//		dojox.storage.FAILED, or dojox.storage.PENDING; these values
		//		determine how the put request went. In some storage systems
		//		users can deny a storage request, resulting in a
		//		dojox.storage.FAILED, while in other storage systems a storage
		//		request must wait for user approval, resulting in a
		//		dojox.storage.PENDING status until the request is either
		//		approved or denied, resulting in another call back with
		//		dojox.storage.SUCCESS. 
		//		The second argument in the call back is the key name that was being stored.
		//		The third argument in the call back is an optional message that
		//		details possible error messages that might have occurred during
		//		the storage process.
		//	namespace:
		//		Optional string namespace that this value will be placed into;
		//		if left off, the value will be placed into dojox.storage.DEFAULT_NAMESPACE
		
		for(var i = 0; i < keys.length; i++){ 
			dojox.storage.put(keys[i], values[i], resultsHandler, namespace); 
		}
	},

	getMultiple: function(/*array*/ keys, /*string?*/ namespace){ /*Object*/
		// summary:
		//		Gets the valuse corresponding to each of the given keys. 
		//		Returns a null array element for each given key that is
		//		not in the storage system.
		// keys:
		//		An array of string keys to get the value of.
		//	namespace:
		//		Optional string namespace that this value will be retrieved from;
		//		if left off, the value will be retrieved from dojox.storage.DEFAULT_NAMESPACE
		// return: Returns any JavaScript object type; null if the key is not present
		
		var results = []; 
		for(var i = 0; i < keys.length; i++){ 
			results.push(dojox.storage.get(keys[i], namespace)); 
		} 
		
		return results;
	},

	removeMultiple: function(/*array*/ keys, /*string?*/ namespace) {
		// summary: Removes the given keys from this storage system.
		
		for(var i = 0; i < keys.length; i++){ 
			dojox.storage.remove(keys[i], namespace); 
		}
	},
	
	isValidKeyArray: function( keys) {
		if(keys === null || keys === undefined || !dojo.isArray(keys)){
			return false;
		}

		//	JAC: This could be optimized by running the key validity test 
		//  directly over a joined string
		return !dojo.some(keys, function(key){
			return !this.isValidKey(key);
		}, this); // Boolean
	},

	hasSettingsUI: function(){ /*Boolean*/
		// summary: Determines whether this provider has a settings UI.
		return false;
	},

	showSettingsUI: function(){
		// summary: If this provider has a settings UI, determined
		// by calling hasSettingsUI(), it is shown. 
		console.warn("dojox.storage.showSettingsUI not implemented");
	},

	hideSettingsUI: function(){
		// summary: If this provider has a settings UI, hides it.
		console.warn("dojox.storage.hideSettingsUI not implemented");
	},
	
	isValidKey: function(/*string*/ keyName){ /*Boolean*/
		// summary:
		//		Subclasses can call this to ensure that the key given is valid
		//		in a consistent way across different storage providers. We use
		//		the lowest common denominator for key values allowed: only
		//		letters, numbers, and underscores are allowed. No spaces. 
		if(keyName === null || keyName === undefined){
			return false;
		}
			
		return /^[0-9A-Za-z_]*$/.test(keyName);
	},
	
	getResourceList: function(){ /* Array[] */
		// summary:
		//	Returns a list of URLs that this
		//	storage provider might depend on.
		// description:
		//	This method returns a list of URLs that this
		//	storage provider depends on to do its work.
		//	This list is used by the Dojo Offline Toolkit
		//	to cache these resources to ensure the machinery
		//	used by this storage provider is available offline.
		//	What is returned is an array of URLs.
		//  Note that Dojo Offline uses Gears as its native 
		//  storage provider, and does not support using other
		//  kinds of storage providers while offline anymore.
		
		return [];
	}
});

}

if(!dojo._hasResource["dojox.storage.manager"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.storage.manager"] = true;
dojo.provide("dojox.storage.manager");
//
// FIXME: refactor this to use an AdapterRegistry

dojox.storage.manager = new function(){
	// summary: A singleton class in charge of the dojox.storage system
	// description:
	//		Initializes the storage systems and figures out the best available 
	//		storage options on this platform.	
	
	// currentProvider: Object
	//	The storage provider that was automagically chosen to do storage
	//	on this platform, such as dojox.storage.FlashStorageProvider.
	this.currentProvider = null;
	
	// available: Boolean
	//	Whether storage of some kind is available.
	this.available = false;

  // providers: Array
  //  Array of all the static provider instances, useful if you want to
  //  loop through and see what providers have been registered.
  this.providers = [];
	
	this._initialized = false;

	this._onLoadListeners = [];
	
	this.initialize = function(){
		// summary: 
		//		Initializes the storage system and autodetects the best storage
		//		provider we can provide on this platform
		this.autodetect();
	};
	
	this.register = function(/*string*/ name, /*Object*/ instance){
		// summary:
		//		Registers the existence of a new storage provider; used by
		//		subclasses to inform the manager of their existence. The
		//		storage manager will select storage providers based on 
		//		their ordering, so the order in which you call this method
		//		matters. 
		// name:
		//		The full class name of this provider, such as
		//		"dojox.storage.FlashStorageProvider".
		// instance:
		//		An instance of this provider, which we will use to call
		//		isAvailable() on. 
		
		// keep list of providers as a list so that we can know what order
		// storage providers are preferred; also, store the providers hashed
		// by name in case someone wants to get a provider that uses
		// a particular storage backend
		this.providers.push(instance);
		this.providers[name] = instance;
	};
	
	this.setProvider = function(storageClass){
		// summary:
		//		Instructs the storageManager to use the given storage class for
		//		all storage requests.
		// description:
		//		Example-
		//			dojox.storage.setProvider(
		//				dojox.storage.IEStorageProvider)
	
	};
	
	this.autodetect = function(){
		// summary:
		//		Autodetects the best possible persistent storage provider
		//		available on this platform. 
		
		//
		
		if(this._initialized){ // already finished
			return;
		}

		// a flag to force the storage manager to use a particular 
		// storage provider type, such as 
		// djConfig = {forceStorageProvider: "dojox.storage.WhatWGStorageProvider"};
		var forceProvider = dojo.config["forceStorageProvider"] || false;

		// go through each provider, seeing if it can be used
		var providerToUse;
		//FIXME: use dojo.some
		for(var i = 0; i < this.providers.length; i++){
			providerToUse = this.providers[i];
			if(forceProvider && forceProvider == providerToUse.declaredClass){
				// still call isAvailable for this provider, since this helps some
				// providers internally figure out if they are available
				// FIXME: This should be refactored since it is non-intuitive
				// that isAvailable() would initialize some state
				providerToUse.isAvailable();
				break;
			}else if(!forceProvider && providerToUse.isAvailable()){
				break;
			}
		}
		
		if(!providerToUse){ // no provider available
			this._initialized = true;
			this.available = false;
			this.currentProvider = null;
			console.warn("No storage provider found for this platform");
			this.loaded();
			return;
		}
			
		// create this provider and mix in it's properties
		// so that developers can do dojox.storage.put rather
		// than dojox.storage.currentProvider.put, for example
		this.currentProvider = providerToUse;
		dojo.mixin(dojox.storage, this.currentProvider);
		
		// have the provider initialize itself
		dojox.storage.initialize();
		
		this._initialized = true;
		this.available = true;
	};
	
	this.isAvailable = function(){ /*Boolean*/
		// summary: Returns whether any storage options are available.
		return this.available;
	};
	
	this.addOnLoad = function(func){ /* void */
		// summary:
		//		Adds an onload listener to know when Dojo Offline can be used.
		// description:
		//		Adds a listener to know when Dojo Offline can be used. This
		//		ensures that the Dojo Offline framework is loaded and that the
		//		local dojox.storage system is ready to be used. This method is
		//		useful if you don't want to have a dependency on Dojo Events
		//		when using dojox.storage.
		// func: Function
		//		A function to call when Dojo Offline is ready to go
		this._onLoadListeners.push(func);
		
		if(this.isInitialized()){
			this._fireLoaded();
		}
	};
	
	this.removeOnLoad = function(func){ /* void */
		// summary: Removes the given onLoad listener
		for(var i = 0; i < this._onLoadListeners.length; i++){
			if(func == this._onLoadListeners[i]){
				this._onLoadListeners = this._onLoadListeners.splice(i, 1);
				break;
			}
		}
	};
	
	this.isInitialized = function(){ /*Boolean*/
	 	// summary:
		//		Returns whether the storage system is initialized and ready to
		//		be used. 

		// FIXME: This should REALLY not be in here, but it fixes a tricky
		// Flash timing bug.
		// Confirm that this is still needed with the newly refactored Dojo
		// Flash. Used to be for Internet Explorer. -- Brad Neuberg
		if(this.currentProvider != null
			&& this.currentProvider.declaredClass == "dojox.storage.FlashStorageProvider" 
			&& dojox.flash.ready == false){
			return false;
		}else{
			return this._initialized;
		}
	};

	this.supportsProvider = function(/*string*/ storageClass){ /* Boolean */
		// summary: Determines if this platform supports the given storage provider.
		// description:
		//		Example-
		//			dojox.storage.manager.supportsProvider(
		//				"dojox.storage.InternetExplorerStorageProvider");

		// construct this class dynamically
		try{
			// dynamically call the given providers class level isAvailable()
			// method
			var provider = eval("new " + storageClass + "()");
			var results = provider.isAvailable();
			if(!results){ return false; }
			return results;
		}catch(e){
			return false;
		}
	};

	this.getProvider = function(){ /* Object */
		// summary: Gets the current provider
		return this.currentProvider;
	};
	
	this.loaded = function(){
		// summary:
		//		The storage provider should call this method when it is loaded
		//		and ready to be used. Clients who will use the provider will
		//		connect to this method to know when they can use the storage
		//		system. You can either use dojo.connect to connect to this
		//		function, or can use dojox.storage.manager.addOnLoad() to add
		//		a listener that does not depend on the dojo.event package.
		// description:
		//		Example 1-
		//			if(dojox.storage.manager.isInitialized() == false){ 
		//				dojo.connect(dojox.storage.manager, "loaded", TestStorage, "initialize");
		//			}else{
		//				dojo.connect(dojo, "loaded", TestStorage, "initialize");
		//			}
		//		Example 2-
		//			dojox.storage.manager.addOnLoad(someFunction);


		// FIXME: we should just provide a Deferred for this. That way you
		// don't care when this happens or has happened. Deferreds are in Base
		this._fireLoaded();
	};
	
	this._fireLoaded = function(){
		//
		
		dojo.forEach(this._onLoadListeners, function(i){ 
			try{ 
				i(); 
			}catch(e){  } 
		});
	};
	
	this.getResourceList = function(){
		// summary:
		//		Returns a list of whatever resources are necessary for storage
		//		providers to work. 
		// description:
		//		This will return all files needed by all storage providers for
		//		this particular environment type. For example, if we are in the
		//		browser environment, then this will return the hidden SWF files
		//		needed by the FlashStorageProvider, even if we don't need them
		//		for the particular browser we are working within. This is meant
		//		to faciliate Dojo Offline, which must retrieve all resources we
		//		need offline into the offline cache -- we retrieve everything
		//		needed, in case another browser that requires different storage
		//		mechanisms hits the local offline cache. For example, if we
		//		were to sync against Dojo Offline on Firefox 2, then we would
		//		not grab the FlashStorageProvider resources needed for Safari.
		var results = [];
		dojo.forEach(dojox.storage.manager.providers, function(currentProvider){
			results = results.concat(currentProvider.getResourceList());
		});
		
		return results;
	}
};

}

if(!dojo._hasResource["dojo.gears"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojo.gears"] = true;
dojo.provide("dojo.gears");

dojo.gears._gearsObject = function(){
	// summary: 
	//		factory method to get a Google Gears plugin instance to
	//		expose in the browser runtime environment, if present
	var factory;
	var results;
	
	var gearsObj = dojo.getObject("google.gears");
	if(gearsObj){ return gearsObj; } // already defined elsewhere
	
	if(typeof GearsFactory != "undefined"){ // Firefox
		factory = new GearsFactory();
	}else{
		if(dojo.isIE){
			// IE
			try{
				factory = new ActiveXObject("Gears.Factory");
			}catch(e){
				// ok to squelch; there's no gears factory.  move on.
			}
		}else if(navigator.mimeTypes["application/x-googlegears"]){
			// Safari?
			factory = document.createElement("object");
			factory.setAttribute("type", "application/x-googlegears");
			factory.setAttribute("width", 0);
			factory.setAttribute("height", 0);
			factory.style.display = "none";
			document.documentElement.appendChild(factory);
		}
	}

	// still nothing?
	if(!factory){ return null; }
	
	// define the global objects now; don't overwrite them though if they
	// were somehow set internally by the Gears plugin, which is on their
	// dev roadmap for the future
	dojo.setObject("google.gears.factory", factory);
	return dojo.getObject("google.gears");
};

/*=====
dojo.gears.available = {
	// summary: True if client is using Google Gears
};
=====*/
// see if we have Google Gears installed, and if
// so, make it available in the runtime environment
// and in the Google standard 'google.gears' global object
dojo.gears.available = (!!dojo.gears._gearsObject())||0;

}

if(!dojo._hasResource["dojox.sql._crypto"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.sql._crypto"] = true;
dojo.provide("dojox.sql._crypto");
dojo.mixin(dojox.sql._crypto, {
	// summary: dojox.sql cryptography code
	// description: 
	//	Taken from http://www.movable-type.co.uk/scripts/aes.html by
	// 	Chris Veness (CLA signed); adapted for Dojo and Google Gears Worker Pool
	// 	by Brad Neuberg, bkn3@columbia.edu
	//
	// _POOL_SIZE:
	//	Size of worker pool to create to help with crypto
	_POOL_SIZE: 100,

	encrypt: function(plaintext, password, callback){
		// summary:
		//	Use Corrected Block TEA to encrypt plaintext using password
		//	(note plaintext & password must be strings not string objects).
		//	Results will be returned to the 'callback' asychronously.	
		this._initWorkerPool();

		var msg ={plaintext: plaintext, password: password};
		msg = dojo.toJson(msg);
		msg = "encr:" + String(msg);

		this._assignWork(msg, callback);
	},

	decrypt: function(ciphertext, password, callback){
		// summary:
		//	Use Corrected Block TEA to decrypt ciphertext using password
		//	(note ciphertext & password must be strings not string objects).
		//	Results will be returned to the 'callback' asychronously.
		this._initWorkerPool();

		var msg = {ciphertext: ciphertext, password: password};
		msg = dojo.toJson(msg);
		msg = "decr:" + String(msg);

		this._assignWork(msg, callback);
	},

	_initWorkerPool: function(){
		// bugs in Google Gears prevents us from dynamically creating
		// and destroying workers as we need them -- the worker
		// pool functionality stops working after a number of crypto
		// cycles (probably related to a memory leak in Google Gears).
		// this is too bad, since it results in much simpler code.

		// instead, we have to create a pool of workers and reuse them. we
		// keep a stack of 'unemployed' Worker IDs that are currently not working.
		// if a work request comes in, we pop off the 'unemployed' stack
		// and put them to work, storing them in an 'employed' hashtable,
		// keyed by their Worker ID with the value being the callback function
		// that wants the result. when an employed worker is done, we get
		// a message in our 'manager' which adds this worker back to the 
		// unemployed stack and routes the result to the callback that
		// wanted it. if all the workers were employed in the past but
		// more work needed to be done (i.e. it's a tight labor pool ;) 
		// then the work messages are pushed onto
		// a 'handleMessage' queue as an object tuple{msg: msg, callback: callback}

		if(!this._manager){
			try{
				this._manager = google.gears.factory.create("beta.workerpool", "1.0");
				this._unemployed = [];
				this._employed ={};
				this._handleMessage = [];
		
				var self = this;
				this._manager.onmessage = function(msg, sender){
					// get the callback necessary to serve this result
					var callback = self._employed["_" + sender];
			
					// make this worker unemployed
					self._employed["_" + sender] = undefined;
					self._unemployed.push("_" + sender);
			
					// see if we need to assign new work
					// that was queued up needing to be done
					if(self._handleMessage.length){
						var handleMe = self._handleMessage.shift();
						self._assignWork(handleMe.msg, handleMe.callback);
					}
			
					// return results
					callback(msg);
				}
			
				var workerInit = "function _workerInit(){"
									+ "gearsWorkerPool.onmessage = "
										+ String(this._workerHandler)
									+ ";"
								+ "}";
		
				var code = workerInit + " _workerInit();";

				// create our worker pool
				for(var i = 0; i < this._POOL_SIZE; i++){
					this._unemployed.push("_" + this._manager.createWorker(code));
				}
			}catch(exp){
				throw exp.message||exp;
			}
		}
	},

	_assignWork: function(msg, callback){
		// can we immediately assign this work?
		if(!this._handleMessage.length && this._unemployed.length){
			// get an unemployed worker
			var workerID = this._unemployed.shift().substring(1); // remove _
	
			// list this worker as employed
			this._employed["_" + workerID] = callback;
	
			// do the worke
			this._manager.sendMessage(msg, parseInt(workerID,10));
		}else{
			// we have to queue it up
			this._handleMessage ={msg: msg, callback: callback};
		}
	},

	_workerHandler: function(msg, sender){
	
		/* Begin AES Implementation */
	
		/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -  */
	
		// Sbox is pre-computed multiplicative inverse in GF(2^8) used in SubBytes and KeyExpansion [§5.1.1]
		var Sbox =	[0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
					 0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
					 0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
					 0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
					 0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
					 0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
					 0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
					 0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
					 0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
					 0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
					 0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
					 0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
					 0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
					 0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
					 0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
					 0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16];

		// Rcon is Round Constant used for the Key Expansion [1st col is 2^(r-1) in GF(2^8)] [§5.2]
		var Rcon = [ [0x00, 0x00, 0x00, 0x00],
					 [0x01, 0x00, 0x00, 0x00],
					 [0x02, 0x00, 0x00, 0x00],
					 [0x04, 0x00, 0x00, 0x00],
					 [0x08, 0x00, 0x00, 0x00],
					 [0x10, 0x00, 0x00, 0x00],
					 [0x20, 0x00, 0x00, 0x00],
					 [0x40, 0x00, 0x00, 0x00],
					 [0x80, 0x00, 0x00, 0x00],
					 [0x1b, 0x00, 0x00, 0x00],
					 [0x36, 0x00, 0x00, 0x00] ]; 

		/*
		 * AES Cipher function: encrypt 'input' with Rijndael algorithm
		 *
		 *	 takes	 byte-array 'input' (16 bytes)
		 *			 2D byte-array key schedule 'w' (Nr+1 x Nb bytes)
		 *
		 *	 applies Nr rounds (10/12/14) using key schedule w for 'add round key' stage
		 *
		 *	 returns byte-array encrypted value (16 bytes)
		 */
		function Cipher(input, w) {	   // main Cipher function [§5.1]
		  var Nb = 4;				// block size (in words): no of columns in state (fixed at 4 for AES)
		  var Nr = w.length/Nb - 1; // no of rounds: 10/12/14 for 128/192/256-bit keys

		  var state = [[],[],[],[]];  // initialise 4xNb byte-array 'state' with input [§3.4]
		  for (var i=0; i<4*Nb; i++) state[i%4][Math.floor(i/4)] = input[i];

		  state = AddRoundKey(state, w, 0, Nb);

		  for (var round=1; round<Nr; round++) {
			state = SubBytes(state, Nb);
			state = ShiftRows(state, Nb);
			state = MixColumns(state, Nb);
			state = AddRoundKey(state, w, round, Nb);
		  }

		  state = SubBytes(state, Nb);
		  state = ShiftRows(state, Nb);
		  state = AddRoundKey(state, w, Nr, Nb);

		  var output = new Array(4*Nb);	 // convert state to 1-d array before returning [§3.4]
		  for (var i=0; i<4*Nb; i++) output[i] = state[i%4][Math.floor(i/4)];
		  return output;
		}


		function SubBytes(s, Nb) {	  // apply SBox to state S [§5.1.1]
		  for (var r=0; r<4; r++) {
			for (var c=0; c<Nb; c++) s[r][c] = Sbox[s[r][c]];
		  }
		  return s;
		}


		function ShiftRows(s, Nb) {	   // shift row r of state S left by r bytes [§5.1.2]
		  var t = new Array(4);
		  for (var r=1; r<4; r++) {
			for (var c=0; c<4; c++) t[c] = s[r][(c+r)%Nb];	// shift into temp copy
			for (var c=0; c<4; c++) s[r][c] = t[c];			// and copy back
		  }			 // note that this will work for Nb=4,5,6, but not 7,8 (always 4 for AES):
		  return s;	 // see fp.gladman.plus.com/cryptography_technology/rijndael/aes.spec.311.pdf 
		}


		function MixColumns(s, Nb) {   // combine bytes of each col of state S [§5.1.3]
		  for (var c=0; c<4; c++) {
			var a = new Array(4);  // 'a' is a copy of the current column from 's'
			var b = new Array(4);  // 'b' is a•{02} in GF(2^8)
			for (var i=0; i<4; i++) {
			  a[i] = s[i][c];
			  b[i] = s[i][c]&0x80 ? s[i][c]<<1 ^ 0x011b : s[i][c]<<1;
			}
			// a[n] ^ b[n] is a•{03} in GF(2^8)
			s[0][c] = b[0] ^ a[1] ^ b[1] ^ a[2] ^ a[3]; // 2*a0 + 3*a1 + a2 + a3
			s[1][c] = a[0] ^ b[1] ^ a[2] ^ b[2] ^ a[3]; // a0 * 2*a1 + 3*a2 + a3
			s[2][c] = a[0] ^ a[1] ^ b[2] ^ a[3] ^ b[3]; // a0 + a1 + 2*a2 + 3*a3
			s[3][c] = a[0] ^ b[0] ^ a[1] ^ a[2] ^ b[3]; // 3*a0 + a1 + a2 + 2*a3
		  }
		  return s;
		}


		function AddRoundKey(state, w, rnd, Nb) {  // xor Round Key into state S [§5.1.4]
		  for (var r=0; r<4; r++) {
			for (var c=0; c<Nb; c++) state[r][c] ^= w[rnd*4+c][r];
		  }
		  return state;
		}


		function KeyExpansion(key) {  // generate Key Schedule (byte-array Nr+1 x Nb) from Key [§5.2]
		  var Nb = 4;			 // block size (in words): no of columns in state (fixed at 4 for AES)
		  var Nk = key.length/4	 // key length (in words): 4/6/8 for 128/192/256-bit keys
		  var Nr = Nk + 6;		 // no of rounds: 10/12/14 for 128/192/256-bit keys

		  var w = new Array(Nb*(Nr+1));
		  var temp = new Array(4);

		  for (var i=0; i<Nk; i++) {
			var r = [key[4*i], key[4*i+1], key[4*i+2], key[4*i+3]];
			w[i] = r;
		  }

		  for (var i=Nk; i<(Nb*(Nr+1)); i++) {
			w[i] = new Array(4);
			for (var t=0; t<4; t++) temp[t] = w[i-1][t];
			if (i % Nk == 0) {
			  temp = SubWord(RotWord(temp));
			  for (var t=0; t<4; t++) temp[t] ^= Rcon[i/Nk][t];
			} else if (Nk > 6 && i%Nk == 4) {
			  temp = SubWord(temp);
			}
			for (var t=0; t<4; t++) w[i][t] = w[i-Nk][t] ^ temp[t];
		  }

		  return w;
		}

		function SubWord(w) {	 // apply SBox to 4-byte word w
		  for (var i=0; i<4; i++) w[i] = Sbox[w[i]];
		  return w;
		}

		function RotWord(w) {	 // rotate 4-byte word w left by one byte
		  w[4] = w[0];
		  for (var i=0; i<4; i++) w[i] = w[i+1];
		  return w;
		}

		/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -  */

		/* 
		 * Use AES to encrypt 'plaintext' with 'password' using 'nBits' key, in 'Counter' mode of operation
		 *							 - see http://csrc.nist.gov/publications/nistpubs/800-38a/sp800-38a.pdf
		 *	 for each block
		 *	 - outputblock = cipher(counter, key)
		 *	 - cipherblock = plaintext xor outputblock
		 */
		function AESEncryptCtr(plaintext, password, nBits) {
		  if (!(nBits==128 || nBits==192 || nBits==256)) return '';	 // standard allows 128/192/256 bit keys

		  // for this example script, generate the key by applying Cipher to 1st 16/24/32 chars of password; 
		  // for real-world applications, a more secure approach would be to hash the password e.g. with SHA-1
		  var nBytes = nBits/8;	 // no bytes in key
		  var pwBytes = new Array(nBytes);
		  for (var i=0; i<nBytes; i++) pwBytes[i] = password.charCodeAt(i) & 0xff;

		  var key = Cipher(pwBytes, KeyExpansion(pwBytes));

		  key = key.concat(key.slice(0, nBytes-16));  // key is now 16/24/32 bytes long

		  // initialise counter block (NIST SP800-38A §B.2): millisecond time-stamp for nonce in 1st 8 bytes,
		  // block counter in 2nd 8 bytes
		  var blockSize = 16;  // block size fixed at 16 bytes / 128 bits (Nb=4) for AES
		  var counterBlock = new Array(blockSize);	// block size fixed at 16 bytes / 128 bits (Nb=4) for AES
		  var nonce = (new Date()).getTime();  // milliseconds since 1-Jan-1970

		  // encode nonce in two stages to cater for JavaScript 32-bit limit on bitwise ops
		  for (var i=0; i<4; i++) counterBlock[i] = (nonce >>> i*8) & 0xff;
		  for (var i=0; i<4; i++) counterBlock[i+4] = (nonce/0x100000000 >>> i*8) & 0xff; 

		  // generate key schedule - an expansion of the key into distinct Key Rounds for each round
		  var keySchedule = KeyExpansion(key);

		  var blockCount = Math.ceil(plaintext.length/blockSize);
		  var ciphertext = new Array(blockCount);  // ciphertext as array of strings
 
		  for (var b=0; b<blockCount; b++) {
			// set counter (block #) in last 8 bytes of counter block (leaving nonce in 1st 8 bytes)
			// again done in two stages for 32-bit ops
			for (var c=0; c<4; c++) counterBlock[15-c] = (b >>> c*8) & 0xff;
			for (var c=0; c<4; c++) counterBlock[15-c-4] = (b/0x100000000 >>> c*8)

			var cipherCntr = Cipher(counterBlock, keySchedule);	 // -- encrypt counter block --

			// calculate length of final block:
			var blockLength = b<blockCount-1 ? blockSize : (plaintext.length-1)%blockSize+1;

			var ct = '';
			for (var i=0; i<blockLength; i++) {	 // -- xor plaintext with ciphered counter byte-by-byte --
			  var plaintextByte = plaintext.charCodeAt(b*blockSize+i);
			  var cipherByte = plaintextByte ^ cipherCntr[i];
			  ct += String.fromCharCode(cipherByte);
			}
			// ct is now ciphertext for this block

			ciphertext[b] = escCtrlChars(ct);  // escape troublesome characters in ciphertext
		  }

		  // convert the nonce to a string to go on the front of the ciphertext
		  var ctrTxt = '';
		  for (var i=0; i<8; i++) ctrTxt += String.fromCharCode(counterBlock[i]);
		  ctrTxt = escCtrlChars(ctrTxt);

		  // use '-' to separate blocks, use Array.join to concatenate arrays of strings for efficiency
		  return ctrTxt + '-' + ciphertext.join('-');
		}


		/* 
		 * Use AES to decrypt 'ciphertext' with 'password' using 'nBits' key, in Counter mode of operation
		 *
		 *	 for each block
		 *	 - outputblock = cipher(counter, key)
		 *	 - cipherblock = plaintext xor outputblock
		 */
		function AESDecryptCtr(ciphertext, password, nBits) {
		  if (!(nBits==128 || nBits==192 || nBits==256)) return '';	 // standard allows 128/192/256 bit keys

		  var nBytes = nBits/8;	 // no bytes in key
		  var pwBytes = new Array(nBytes);
		  for (var i=0; i<nBytes; i++) pwBytes[i] = password.charCodeAt(i) & 0xff;
		  var pwKeySchedule = KeyExpansion(pwBytes);
		  var key = Cipher(pwBytes, pwKeySchedule);
		  key = key.concat(key.slice(0, nBytes-16));  // key is now 16/24/32 bytes long

		  var keySchedule = KeyExpansion(key);

		  ciphertext = ciphertext.split('-');  // split ciphertext into array of block-length strings 

		  // recover nonce from 1st element of ciphertext
		  var blockSize = 16;  // block size fixed at 16 bytes / 128 bits (Nb=4) for AES
		  var counterBlock = new Array(blockSize);
		  var ctrTxt = unescCtrlChars(ciphertext[0]);
		  for (var i=0; i<8; i++) counterBlock[i] = ctrTxt.charCodeAt(i);

		  var plaintext = new Array(ciphertext.length-1);

		  for (var b=1; b<ciphertext.length; b++) {
			// set counter (block #) in last 8 bytes of counter block (leaving nonce in 1st 8 bytes)
			for (var c=0; c<4; c++) counterBlock[15-c] = ((b-1) >>> c*8) & 0xff;
			for (var c=0; c<4; c++) counterBlock[15-c-4] = ((b/0x100000000-1) >>> c*8) & 0xff;

			var cipherCntr = Cipher(counterBlock, keySchedule);	 // encrypt counter block

			ciphertext[b] = unescCtrlChars(ciphertext[b]);

			var pt = '';
			for (var i=0; i<ciphertext[b].length; i++) {
			  // -- xor plaintext with ciphered counter byte-by-byte --
			  var ciphertextByte = ciphertext[b].charCodeAt(i);
			  var plaintextByte = ciphertextByte ^ cipherCntr[i];
			  pt += String.fromCharCode(plaintextByte);
			}
			// pt is now plaintext for this block

			plaintext[b-1] = pt;  // b-1 'cos no initial nonce block in plaintext
		  }

		  return plaintext.join('');
		}

		/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -  */

		function escCtrlChars(str) {  // escape control chars which might cause problems handling ciphertext
		  return str.replace(/[\0\t\n\v\f\r\xa0!-]/g, function(c) { return '!' + c.charCodeAt(0) + '!'; });
		}  // \xa0 to cater for bug in Firefox; include '-' to leave it free for use as a block marker

		function unescCtrlChars(str) {	// unescape potentially problematic control characters
		  return str.replace(/!\d\d?\d?!/g, function(c) { return String.fromCharCode(c.slice(1,-1)); });
		}

		/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -  */
	
		function encrypt(plaintext, password){
			return AESEncryptCtr(plaintext, password, 256);
		}

		function decrypt(ciphertext, password){	
			return AESDecryptCtr(ciphertext, password, 256);
		}
	
		/* End AES Implementation */
	
		var cmd = msg.substr(0,4);
		var arg = msg.substr(5);
		if(cmd == "encr"){
			arg = eval("(" + arg + ")");
			var plaintext = arg.plaintext;
			var password = arg.password;
			var results = encrypt(plaintext, password);
			gearsWorkerPool.sendMessage(String(results), sender);
		}else if(cmd == "decr"){
			arg = eval("(" + arg + ")");
			var ciphertext = arg.ciphertext;
			var password = arg.password;
			var results = decrypt(ciphertext, password);
			gearsWorkerPool.sendMessage(String(results), sender);
		}
	}
});

}

if(!dojo._hasResource["dojox.sql._base"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.sql._base"] = true;
dojo.provide("dojox.sql._base");


dojo.mixin(dojox.sql, {
	// summary:
	//	Executes a SQL expression.
	// description:
	// 	There are four ways to call this:
	// 	1) Straight SQL: dojox.sql("SELECT * FROM FOOBAR");
	// 	2) SQL with parameters: dojox.sql("INSERT INTO FOOBAR VALUES (?)", someParam)
	// 	3) Encrypting particular values: 
	//			dojox.sql("INSERT INTO FOOBAR VALUES (ENCRYPT(?))", someParam, "somePassword", callback)
	// 	4) Decrypting particular values:
	//			dojox.sql("SELECT DECRYPT(SOMECOL1), DECRYPT(SOMECOL2) FROM
	//					FOOBAR WHERE SOMECOL3 = ?", someParam,
	//					"somePassword", callback)
	//
	// 	For encryption and decryption the last two values should be the the password for
	// 	encryption/decryption, and the callback function that gets the result set.
	//
	// 	Note: We only support ENCRYPT(?) statements, and
	// 	and DECRYPT(*) statements for now -- you can not have a literal string
	// 	inside of these, such as ENCRYPT('foobar')
	//
	// 	Note: If you have multiple columns to encrypt and decrypt, you can use the following
	// 	convenience form to not have to type ENCRYPT(?)/DECRYPT(*) many times:
	//
	// 	dojox.sql("INSERT INTO FOOBAR VALUES (ENCRYPT(?, ?, ?))", 
	//					someParam1, someParam2, someParam3, 
	//					"somePassword", callback)
	//
	// 	dojox.sql("SELECT DECRYPT(SOMECOL1, SOMECOL2) FROM
	//					FOOBAR WHERE SOMECOL3 = ?", someParam,
	//					"somePassword", callback)

	dbName: null,
	
	// summary:
	//	If true, then we print out any SQL that is executed
	//	to the debug window
	debug: (dojo.exists("dojox.sql.debug") ? dojox.sql.debug:false),

	open: function(dbName){
		if(this._dbOpen && (!dbName || dbName == this.dbName)){
			return;
		}
		
		if(!this.dbName){
			this.dbName = "dot_store_" 
				+ window.location.href.replace(/[^0-9A-Za-z_]/g, "_");
			// database names in Gears are limited to 64 characters long
			if(this.dbName.length > 63){
			  this.dbName = this.dbName.substring(0, 63);
			}
		}
		
		if(!dbName){
			dbName = this.dbName;
		}
		
		try{
			this._initDb();
			this.db.open(dbName);
			this._dbOpen = true;
		}catch(exp){
			throw exp.message||exp;
		}
	},

	close: function(dbName){
		// on Internet Explorer, Google Gears throws an exception
		// "Object not a collection", when we try to close the
		// database -- just don't close it on this platform
		// since we are running into a Gears bug; the Gears team
		// said it's ok to not close a database connection
		if(dojo.isIE){ return; }
		
		if(!this._dbOpen && (!dbName || dbName == this.dbName)){
			return;
		}
		
		if(!dbName){
			dbName = this.dbName;
		}
		
		try{
			this.db.close(dbName);
			this._dbOpen = false;
		}catch(exp){
			throw exp.message||exp;
		}
	},
	
	_exec: function(params){
		try{	
			// get the Gears Database object
			this._initDb();
		
			// see if we need to open the db; if programmer
			// manually called dojox.sql.open() let them handle
			// it; otherwise we open and close automatically on
			// each SQL execution
			if(!this._dbOpen){
				this.open();
				this._autoClose = true;
			}
		
			// determine our parameters
			var sql = null;
			var callback = null;
			var password = null;

			var args = dojo._toArray(params);

			sql = args.splice(0, 1)[0];

			// does this SQL statement use the ENCRYPT or DECRYPT
			// keywords? if so, extract our callback and crypto
			// password
			if(this._needsEncrypt(sql) || this._needsDecrypt(sql)){
				callback = args.splice(args.length - 1, 1)[0];
				password = args.splice(args.length - 1, 1)[0];
			}

			// 'args' now just has the SQL parameters

			// print out debug SQL output if the developer wants that
			if(this.debug){
				this._printDebugSQL(sql, args);
			}

			// handle SQL that needs encryption/decryption differently
			// do we have an ENCRYPT SQL statement? if so, handle that first
			var crypto;
			if(this._needsEncrypt(sql)){
				crypto = new dojox.sql._SQLCrypto("encrypt", sql, 
													password, args, 
													callback);
				return null; // encrypted results will arrive asynchronously
			}else if(this._needsDecrypt(sql)){ // otherwise we have a DECRYPT statement
				crypto = new dojox.sql._SQLCrypto("decrypt", sql, 
													password, args, 
													callback);
				return null; // decrypted results will arrive asynchronously
			}

			// execute the SQL and get the results
			var rs = this.db.execute(sql, args);
			
			// Gears ResultSet object's are ugly -- normalize
			// these into something JavaScript programmers know
			// how to work with, basically an array of 
			// JavaScript objects where each property name is
			// simply the field name for a column of data
			rs = this._normalizeResults(rs);
		
			if(this._autoClose){
				this.close();
			}
		
			return rs;
		}catch(exp){
			exp = exp.message||exp;
			
			
			
			if(this._autoClose){
				try{ 
					this.close(); 
				}catch(e){
					
				}
			}
		
			throw exp;
		}
		
		return null;
	},

	_initDb: function(){
		if(!this.db){
			try{
				this.db = google.gears.factory.create('beta.database', '1.0');
			}catch(exp){
				dojo.setObject("google.gears.denied", true);
				if(dojox.off){
				  dojox.off.onFrameworkEvent("coreOperationFailed");
				}
				throw "Google Gears must be allowed to run";
			}
		}
	},

	_printDebugSQL: function(sql, args){
		var msg = "dojox.sql(\"" + sql + "\"";
		for(var i = 0; i < args.length; i++){
			if(typeof args[i] == "string"){
				msg += ", \"" + args[i] + "\"";
			}else{
				msg += ", " + args[i];
			}
		}
		msg += ")";
	
		
	},

	_normalizeResults: function(rs){
		var results = [];
		if(!rs){ return []; }
	
		while(rs.isValidRow()){
			var row = {};
		
			for(var i = 0; i < rs.fieldCount(); i++){
				var fieldName = rs.fieldName(i);
				var fieldValue = rs.field(i);
				row[fieldName] = fieldValue;
			}
		
			results.push(row);
		
			rs.next();
		}
	
		rs.close();
		
		return results;
	},

	_needsEncrypt: function(sql){
		return /encrypt\([^\)]*\)/i.test(sql);
	},

	_needsDecrypt: function(sql){
		return /decrypt\([^\)]*\)/i.test(sql);
	}
});

dojo.declare("dojox.sql._SQLCrypto", null, {
	// summary:
	//	A private class encapsulating any cryptography that must be done
	// 	on a SQL statement. We instantiate this class and have it hold
	//	it's state so that we can potentially have several encryption
	//	operations happening at the same time by different SQL statements.	
	constructor: function(action, sql, password, args, callback){
		if(action == "encrypt"){
			this._execEncryptSQL(sql, password, args, callback);
		}else{
			this._execDecryptSQL(sql, password, args, callback);
		}		
	}, 
	
	_execEncryptSQL: function(sql, password, args, callback){
		// strip the ENCRYPT/DECRYPT keywords from the SQL
		var strippedSQL = this._stripCryptoSQL(sql);
	
		// determine what arguments need encryption
		var encryptColumns = this._flagEncryptedArgs(sql, args);
	
		// asynchronously encrypt each argument that needs it
		var self = this;
		this._encrypt(strippedSQL, password, args, encryptColumns, function(finalArgs){
			// execute the SQL
			var error = false;
			var resultSet = [];
			var exp = null;
			try{
				resultSet = dojox.sql.db.execute(strippedSQL, finalArgs);
			}catch(execError){
				error = true;
				exp = execError.message||execError;
			}
		
			// was there an error during SQL execution?
			if(exp != null){
				if(dojox.sql._autoClose){
					try{ dojox.sql.close(); }catch(e){}
				}
			
				callback(null, true, exp.toString());
				return;
			}
		
			// normalize SQL results into a JavaScript object 
			// we can work with
			resultSet = dojox.sql._normalizeResults(resultSet);
		
			if(dojox.sql._autoClose){
				dojox.sql.close();
			}
				
			// are any decryptions necessary on the result set?
			if(dojox.sql._needsDecrypt(sql)){
				// determine which of the result set columns needs decryption
	 			var needsDecrypt = self._determineDecryptedColumns(sql);

				// now decrypt columns asynchronously
				// decrypt columns that need it
				self._decrypt(resultSet, needsDecrypt, password, function(finalResultSet){
					callback(finalResultSet, false, null);
				});
			}else{
				callback(resultSet, false, null);
			}
		});
	},

	_execDecryptSQL: function(sql, password, args, callback){
		// strip the ENCRYPT/DECRYPT keywords from the SQL
		var strippedSQL = this._stripCryptoSQL(sql);
	
		// determine which columns needs decryption; this either
		// returns the value *, which means all result set columns will
		// be decrypted, or it will return the column names that need
		// decryption set on a hashtable so we can quickly test a given
		// column name; the key is the column name that needs
		// decryption and the value is 'true' (i.e. needsDecrypt["someColumn"] 
		// would return 'true' if it needs decryption, and would be 'undefined'
		// or false otherwise)
		var needsDecrypt = this._determineDecryptedColumns(sql);
	
		// execute the SQL
		var error = false;
		var resultSet = [];
		var exp = null;
		try{
			resultSet = dojox.sql.db.execute(strippedSQL, args);
		}catch(execError){
			error = true;
			exp = execError.message||execError;
		}
	
		// was there an error during SQL execution?
		if(exp != null){
			if(dojox.sql._autoClose){
				try{ dojox.sql.close(); }catch(e){}
			}
		
			callback(resultSet, true, exp.toString());
			return;
		}
	
		// normalize SQL results into a JavaScript object 
		// we can work with
		resultSet = dojox.sql._normalizeResults(resultSet);
	
		if(dojox.sql._autoClose){
			dojox.sql.close();
		}
	
		// decrypt columns that need it
		this._decrypt(resultSet, needsDecrypt, password, function(finalResultSet){
			callback(finalResultSet, false, null);
		});
	},

	_encrypt: function(sql, password, args, encryptColumns, callback){
		//
	
		this._totalCrypto = 0;
		this._finishedCrypto = 0;
		this._finishedSpawningCrypto = false;
		this._finalArgs = args;
	
		for(var i = 0; i < args.length; i++){
			if(encryptColumns[i]){
				// we have an encrypt() keyword -- get just the value inside
				// the encrypt() parantheses -- for now this must be a ?
				var sqlParam = args[i];
				var paramIndex = i;
			
				// update the total number of encryptions we know must be done asynchronously
				this._totalCrypto++;
			
				// FIXME: This currently uses DES as a proof-of-concept since the
				// DES code used is quite fast and was easy to work with. Modify dojox.sql
				// to be able to specify a different encryption provider through a 
				// a SQL-like syntax, such as dojox.sql("SET ENCRYPTION BLOWFISH"),
				// and modify the dojox.crypto.Blowfish code to be able to work using
				// a Google Gears Worker Pool
			
				// do the actual encryption now, asychronously on a Gears worker thread
				dojox.sql._crypto.encrypt(sqlParam, password, dojo.hitch(this, function(results){
					// set the new encrypted value
					this._finalArgs[paramIndex] = results;
					this._finishedCrypto++;
					// are we done with all encryption?
					if(this._finishedCrypto >= this._totalCrypto
						&& this._finishedSpawningCrypto){
						callback(this._finalArgs);
					}
				}));
			}
		}
	
		this._finishedSpawningCrypto = true;
	},

	_decrypt: function(resultSet, needsDecrypt, password, callback){
		//
		
		this._totalCrypto = 0;
		this._finishedCrypto = 0;
		this._finishedSpawningCrypto = false;
		this._finalResultSet = resultSet;
	
		for(var i = 0; i < resultSet.length; i++){
			var row = resultSet[i];
		
			// go through each of the column names in row,
			// seeing if they need decryption
			for(var columnName in row){
				if(needsDecrypt == "*" || needsDecrypt[columnName]){
					this._totalCrypto++;
					var columnValue = row[columnName];
				
					// forming a closure here can cause issues, with values not cleanly
					// saved on Firefox/Mac OS X for some of the values above that
					// are needed in the callback below; call a subroutine that will form 
					// a closure inside of itself instead
					this._decryptSingleColumn(columnName, columnValue, password, i,
												function(finalResultSet){
						callback(finalResultSet);
					});
				}
			}
		}
	
		this._finishedSpawningCrypto = true;
	},

	_stripCryptoSQL: function(sql){
		// replace all DECRYPT(*) occurrences with a *
		sql = sql.replace(/DECRYPT\(\*\)/ig, "*");
	
		// match any ENCRYPT(?, ?, ?, etc) occurrences,
		// then replace with just the question marks in the
		// middle
		var matches = sql.match(/ENCRYPT\([^\)]*\)/ig);
		if(matches != null){
			for(var i = 0; i < matches.length; i++){
				var encryptStatement = matches[i];
				var encryptValue = encryptStatement.match(/ENCRYPT\(([^\)]*)\)/i)[1];
				sql = sql.replace(encryptStatement, encryptValue);
			}
		}
	
		// match any DECRYPT(COL1, COL2, etc) occurrences,
		// then replace with just the column names
		// in the middle
		matches = sql.match(/DECRYPT\([^\)]*\)/ig);
		if(matches != null){
			for(i = 0; i < matches.length; i++){
				var decryptStatement = matches[i];
				var decryptValue = decryptStatement.match(/DECRYPT\(([^\)]*)\)/i)[1];
				sql = sql.replace(decryptStatement, decryptValue);
			}
		}
	
		return sql;
	},

	_flagEncryptedArgs: function(sql, args){
		// capture literal strings that have question marks in them,
		// and also capture question marks that stand alone
		var tester = new RegExp(/([\"][^\"]*\?[^\"]*[\"])|([\'][^\']*\?[^\']*[\'])|(\?)/ig);
		var matches;
		var currentParam = 0;
		var results = [];
		while((matches = tester.exec(sql)) != null){
			var currentMatch = RegExp.lastMatch+"";

			// are we a literal string? then ignore it
			if(/^[\"\']/.test(currentMatch)){
				continue;
			}

			// do we have an encrypt keyword to our left?
			var needsEncrypt = false;
			if(/ENCRYPT\([^\)]*$/i.test(RegExp.leftContext)){
				needsEncrypt = true;
			}

			// set the encrypted flag
			results[currentParam] = needsEncrypt;

			currentParam++;
		}
	
		return results;
	},

	_determineDecryptedColumns: function(sql){
		var results = {};

		if(/DECRYPT\(\*\)/i.test(sql)){
			results = "*";
		}else{
			var tester = /DECRYPT\((?:\s*\w*\s*\,?)*\)/ig;
			var matches = tester.exec(sql);
			while(matches){
				var lastMatch = new String(RegExp.lastMatch);
				var columnNames = lastMatch.replace(/DECRYPT\(/i, "");
				columnNames = columnNames.replace(/\)/, "");
				columnNames = columnNames.split(/\s*,\s*/);
				dojo.forEach(columnNames, function(column){
					if(/\s*\w* AS (\w*)/i.test(column)){
						column = column.match(/\s*\w* AS (\w*)/i)[1];
					}
					results[column] = true;
				});
				
				matches = tester.exec(sql)
			}
		}

		return results;
	},

	_decryptSingleColumn: function(columnName, columnValue, password, currentRowIndex,
											callback){
		//
		dojox.sql._crypto.decrypt(columnValue, password, dojo.hitch(this, function(results){
			// set the new decrypted value
			this._finalResultSet[currentRowIndex][columnName] = results;
			this._finishedCrypto++;
			
			// are we done with all encryption?
			if(this._finishedCrypto >= this._totalCrypto
				&& this._finishedSpawningCrypto){
				//
				callback(this._finalResultSet);
			}
		}));
	}
});

(function(){

	var orig_sql = dojox.sql;
	dojox.sql = new Function("return dojox.sql._exec(arguments);");
	dojo.mixin(dojox.sql, orig_sql);
	
})();

}

if(!dojo._hasResource["dojox.sql"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.sql"] = true;
dojo.provide("dojox.sql"); 


}

if(!dojo._hasResource["dojox.storage.GearsStorageProvider"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.storage.GearsStorageProvider"] = true;
dojo.provide("dojox.storage.GearsStorageProvider");





if(dojo.gears.available){
	
	(function(){
		// make sure we don't define the gears provider if we're not gears
		// enabled
		
		dojo.declare("dojox.storage.GearsStorageProvider", dojox.storage.Provider, {
			// summary:
			//		Storage provider that uses the features of Google Gears
			//		to store data (it is saved into the local SQL database
			//		provided by Gears, using dojox.sql)
			// description: 
			//		You can disable this storage provider with the following djConfig
			//		variable:
			//		var djConfig = { disableGearsStorage: true };
			//		
			//		Authors of this storage provider-	
			//			Brad Neuberg, bkn3@columbia.edu 
			constructor: function(){
			},
			// instance methods and properties
			TABLE_NAME: "__DOJO_STORAGE",
			initialized: false,
			
			_available: null,
			_storageReady: false,
			
			initialize: function(){
				//
				if(dojo.config["disableGearsStorage"] == true){
					return;
				}
				
				// partition our storage data so that multiple apps
				// on the same host won't collide
				this.TABLE_NAME = "__DOJO_STORAGE";
				
				// we delay creating our internal tables until an operation is
				// actually called, to avoid having a Gears permission dialog
				// on page load (bug #7538)
				
				// indicate that this storage provider is now loaded
				this.initialized = true;
				dojox.storage.manager.loaded();	
			},
			
			isAvailable: function(){
				// is Google Gears available and defined?
				return this._available = dojo.gears.available;
			},

			put: function(key, value, resultsHandler, namespace){
				this._initStorage();
				
				if(!this.isValidKey(key)){
					throw new Error("Invalid key given: " + key);
				}
				
				namespace = namespace||this.DEFAULT_NAMESPACE;
				if(!this.isValidKey(namespace)){
					throw new Error("Invalid namespace given: " + key);
				}
				
				// serialize the value;
				// handle strings differently so they have better performance
				if(dojo.isString(value)){
					value = "string:" + value;
				}else{
					value = dojo.toJson(value);
				}
				
				// try to store the value	
				try{
					dojox.sql("DELETE FROM " + this.TABLE_NAME
								+ " WHERE namespace = ? AND key = ?",
								namespace, key);
					dojox.sql("INSERT INTO " + this.TABLE_NAME
								+ " VALUES (?, ?, ?)",
								namespace, key, value);
				}catch(e){
					// indicate we failed
					
					resultsHandler(this.FAILED, key, e.toString(), namespace);
					return;
				}
				
				if(resultsHandler){
					resultsHandler(dojox.storage.SUCCESS, key, null, namespace);
				}
			},

			get: function(key, namespace){
				this._initStorage();
				
				if(!this.isValidKey(key)){
					throw new Error("Invalid key given: " + key);
				}
				
				namespace = namespace||this.DEFAULT_NAMESPACE;
				if(!this.isValidKey(namespace)){
					throw new Error("Invalid namespace given: " + key);
				}
				
				// try to find this key in the database
				var results = dojox.sql("SELECT * FROM " + this.TABLE_NAME
											+ " WHERE namespace = ? AND "
											+ " key = ?",
											namespace, key);
				if(!results.length){
					return null;
				}else{
					results = results[0].value;
				}
				
				// destringify the content back into a 
				// real JavaScript object;
				// handle strings differently so they have better performance
				if(dojo.isString(results) && (/^string:/.test(results))){
					results = results.substring("string:".length);
				}else{
					results = dojo.fromJson(results);
				}
				
				return results;
			},
			
			getNamespaces: function(){
				this._initStorage();
				
				var results = [ dojox.storage.DEFAULT_NAMESPACE ];
				
				var rs = dojox.sql("SELECT namespace FROM " + this.TABLE_NAME
									+ " DESC GROUP BY namespace");
				for(var i = 0; i < rs.length; i++){
					if(rs[i].namespace != dojox.storage.DEFAULT_NAMESPACE){
						results.push(rs[i].namespace);
					}
				}
				
				return results;
			},

			getKeys: function(namespace){
				this._initStorage();
				
				namespace = namespace||this.DEFAULT_NAMESPACE;
				if(!this.isValidKey(namespace)){
					throw new Error("Invalid namespace given: " + namespace);
				}
				
				var rs = dojox.sql("SELECT key FROM " + this.TABLE_NAME
									+ " WHERE namespace = ?",
									namespace);
				
				var results = [];
				for(var i = 0; i < rs.length; i++){
					results.push(rs[i].key);
				}
				
				return results;
			},

			clear: function(namespace){
				this._initStorage();
				
				namespace = namespace||this.DEFAULT_NAMESPACE;
				if(!this.isValidKey(namespace)){
					throw new Error("Invalid namespace given: " + namespace);
				}
				
				dojox.sql("DELETE FROM " + this.TABLE_NAME 
							+ " WHERE namespace = ?",
							namespace);
			},
			
			remove: function(key, namespace){
				this._initStorage();
				
				if(!this.isValidKey(key)){
					throw new Error("Invalid key given: " + key);
				}
				
				namespace = namespace||this.DEFAULT_NAMESPACE;
				if(!this.isValidKey(namespace)){
					throw new Error("Invalid namespace given: " + key);
				}
				
				dojox.sql("DELETE FROM " + this.TABLE_NAME 
							+ " WHERE namespace = ? AND"
							+ " key = ?",
							namespace,
							key);
			},
			
			putMultiple: function(keys, values, resultsHandler, namespace) {
				this._initStorage();
				
 				if(!this.isValidKeyArray(keys) 
						|| ! values instanceof Array 
						|| keys.length != values.length){
					throw new Error("Invalid arguments: keys = [" 
									+ keys + "], values = [" + values + "]");
				}
				
				if(namespace == null || typeof namespace == "undefined"){
					namespace = dojox.storage.DEFAULT_NAMESPACE;		
				}
				if(!this.isValidKey(namespace)){
					throw new Error("Invalid namespace given: " + namespace);
				}
	
				this._statusHandler = resultsHandler;

				// try to store the value	
				try{
					dojox.sql.open();
					dojox.sql.db.execute("BEGIN TRANSACTION");
					var _stmt = "REPLACE INTO " + this.TABLE_NAME + " VALUES (?, ?, ?)";
					for(var i=0;i<keys.length;i++) {
						// serialize the value;
						// handle strings differently so they have better performance
						var value = values[i];
						if(dojo.isString(value)){
							value = "string:" + value;
						}else{
							value = dojo.toJson(value);
						}
				
						dojox.sql.db.execute( _stmt,
							[namespace, keys[i], value]);
					}
					dojox.sql.db.execute("COMMIT TRANSACTION");
					dojox.sql.close();
				}catch(e){
					// indicate we failed
					
					if(resultsHandler){
						resultsHandler(this.FAILED, keys, e.toString(), namespace);
					}
					return;
				}
				
				if(resultsHandler){
					resultsHandler(dojox.storage.SUCCESS, keys, null, namespace);
				}
			},

			getMultiple: function(keys, namespace){
				//	TODO: Maybe use SELECT IN instead
				this._initStorage();

				if(!this.isValidKeyArray(keys)){
					throw new ("Invalid key array given: " + keys);
				}
				
				if(namespace == null || typeof namespace == "undefined"){
					namespace = dojox.storage.DEFAULT_NAMESPACE;		
				}
				if(!this.isValidKey(namespace)){
					throw new Error("Invalid namespace given: " + namespace);
				}
		
				var _stmt = "SELECT * FROM " + this.TABLE_NAME 
					+ " WHERE namespace = ? AND "	+ " key = ?";
				
				var results = [];
				for(var i=0;i<keys.length;i++){
					var result = dojox.sql( _stmt, namespace, keys[i]);
						
					if( ! result.length){
						results[i] = null;
					}else{
						result = result[0].value;
						
						// destringify the content back into a 
						// real JavaScript object;
						// handle strings differently so they have better performance
						if(dojo.isString(result) && (/^string:/.test(result))){
							results[i] = result.substring("string:".length);
						}else{
							results[i] = dojo.fromJson(result);
						}
					}
				}
				
				return results;
			},
			
			removeMultiple: function(keys, namespace){
				this._initStorage();
				
				if(!this.isValidKeyArray(keys)){
					throw new Error("Invalid arguments: keys = [" + keys + "]");
				}
				
				if(namespace == null || typeof namespace == "undefined"){
					namespace = dojox.storage.DEFAULT_NAMESPACE;		
				}
				if(!this.isValidKey(namespace)){
					throw new Error("Invalid namespace given: " + namespace);
				}
				
				dojox.sql.open();
				dojox.sql.db.execute("BEGIN TRANSACTION");
				var _stmt = "DELETE FROM " + this.TABLE_NAME 
										+ " WHERE namespace = ? AND key = ?";

				for(var i=0;i<keys.length;i++){
					dojox.sql.db.execute( _stmt,
						[namespace, keys[i]]);
				}
				dojox.sql.db.execute("COMMIT TRANSACTION");
				dojox.sql.close();
			}, 				
			
			isPermanent: function(){ return true; },

			getMaximumSize: function(){ return this.SIZE_NO_LIMIT; },

			hasSettingsUI: function(){ return false; },
			
			showSettingsUI: function(){
				throw new Error(this.declaredClass 
									+ " does not support a storage settings user-interface");
			},
			
			hideSettingsUI: function(){
				throw new Error(this.declaredClass 
									+ " does not support a storage settings user-interface");
			},
			
			_initStorage: function(){
				// we delay creating the tables until an operation is actually
				// called so that we don't give a Gears dialog right on page
				// load (bug #7538)
				if (this._storageReady) {
					return;
				}
				
				if (!google.gears.factory.hasPermission) {
					var siteName = null;
					var icon = null;
					var msg = 'This site would like to use Google Gears to enable '
										+ 'enhanced functionality.';
					var allowed = google.gears.factory.getPermission(siteName, icon, msg);
					if (!allowed) {
						throw new Error('You must give permission to use Gears in order to '
														+ 'store data');
					}
				}
				
				// create the table that holds our data
				try{
					dojox.sql("CREATE TABLE IF NOT EXISTS " + this.TABLE_NAME + "( "
								+ " namespace TEXT, "
								+ " key TEXT, "
								+ " value TEXT "
								+ ")"
							);
					dojox.sql("CREATE UNIQUE INDEX IF NOT EXISTS namespace_key_index" 
								+ " ON " + this.TABLE_NAME
								+ " (namespace, key)");
				}catch(e){
					
					throw new Error('Unable to create storage tables for Gears in '
					                + 'Dojo Storage');
				}
				
				this._storageReady = true;
		  }
		});

		// register the existence of our storage providers
		dojox.storage.manager.register("dojox.storage.GearsStorageProvider",
										new dojox.storage.GearsStorageProvider());
	})();
}

}

if(!dojo._hasResource["dojox.storage.WhatWGStorageProvider"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.storage.WhatWGStorageProvider"] = true;
dojo.provide("dojox.storage.WhatWGStorageProvider");



dojo.declare("dojox.storage.WhatWGStorageProvider", [ dojox.storage.Provider ], {
	// summary:
	//		Storage provider that uses WHAT Working Group features in Firefox 2 
	//		to achieve permanent storage.
	// description: 
	//		The WHAT WG storage API is documented at 
	//		http://www.whatwg.org/specs/web-apps/current-work/#scs-client-side
	//
	//		You can disable this storage provider with the following djConfig
	//		variable:
	//		var djConfig = { disableWhatWGStorage: true };
	//		
	//		Authors of this storage provider-	
	//			JB Boisseau, jb.boisseau@eutech-ssii.com
	//			Brad Neuberg, bkn3@columbia.edu 

	initialized: false,
	
	_domain: null,
	_available: null,
	_statusHandler: null,
	_allNamespaces: null,
	_storageEventListener: null,
	
	initialize: function(){
		if(dojo.config["disableWhatWGStorage"] == true){
			return;
		}
		
		// get current domain
		this._domain = this._getDomain();
		// 
		
		// indicate that this storage provider is now loaded
		this.initialized = true;
		dojox.storage.manager.loaded();	
	},
	
	isAvailable: function(){
		try{
			var myStorage = globalStorage[this._getDomain()]; 
		}catch(e){
			this._available = false;
			return this._available;
		}
		
		this._available = true;	
		return this._available;
	},

	put: function(key, value, resultsHandler, namespace){
		if(this.isValidKey(key) == false){
			throw new Error("Invalid key given: " + key);
		}
		namespace = namespace||this.DEFAULT_NAMESPACE;
		
		// get our full key name, which is namespace + key
		key = this.getFullKey(key, namespace);	
		
		this._statusHandler = resultsHandler;
		
		// serialize the value;
		// handle strings differently so they have better performance
		if(dojo.isString(value)){
			value = "string:" + value;
		}else{
			value = dojo.toJson(value);
		}
		
		// register for successful storage events.
		var storageListener = dojo.hitch(this, function(evt){
			// remove any old storage event listener we might have added
			// to the window on old put() requests; Firefox has a bug
			// where it can occassionaly go into infinite loops calling
			// our storage event listener over and over -- this is a 
			// workaround
			// FIXME: Simplify this into a test case and submit it
			// to Firefox
			window.removeEventListener("storage", storageListener, false);
			
			// indicate we succeeded
			if(resultsHandler){
				resultsHandler.call(null, this.SUCCESS, key, null, namespace);
			}
		});
		
		window.addEventListener("storage", storageListener, false);
		
		// try to store the value	
		try{
			var myStorage = globalStorage[this._domain];
			myStorage.setItem(key, value);
		}catch(e){
			// indicate we failed
			this._statusHandler.call(null, this.FAILED, key, e.toString(), namespace);
		}
	},

	get: function(key, namespace){
		if(this.isValidKey(key) == false){
			throw new Error("Invalid key given: " + key);
		}
		namespace = namespace||this.DEFAULT_NAMESPACE;
		
		// get our full key name, which is namespace + key
		key = this.getFullKey(key, namespace);
		
		// sometimes, even if a key doesn't exist, Firefox
		// will return a blank string instead of a null --
		// this _might_ be due to having underscores in the
		// keyname, but I am not sure.
		
		// FIXME: Simplify this bug into a testcase and
		// submit it to Firefox
		var myStorage = globalStorage[this._domain];
		var results = myStorage.getItem(key);
		
		if(results == null || results == ""){
			return null;
		}
		
		results = results.value;
		
		// destringify the content back into a 
		// real JavaScript object;
		// handle strings differently so they have better performance
		if(dojo.isString(results) && (/^string:/.test(results))){
			results = results.substring("string:".length);
		}else{
			results = dojo.fromJson(results);
		}
		
		return results;
	},
	
	getNamespaces: function(){
		var results = [ this.DEFAULT_NAMESPACE ];
		
		// simply enumerate through our array and save any string
		// that starts with __
		var found = {};
		var myStorage = globalStorage[this._domain];
		var tester = /^__([^_]*)_/;
		for(var i = 0; i < myStorage.length; i++){
			var currentKey = myStorage.key(i);
			if(tester.test(currentKey) == true){
				var currentNS = currentKey.match(tester)[1];
				// have we seen this namespace before?
				if(typeof found[currentNS] == "undefined"){
					found[currentNS] = true;
					results.push(currentNS);
				}
			}
		}
		
		return results;
	},

	getKeys: function(namespace){
		namespace = namespace||this.DEFAULT_NAMESPACE;
		
		if(this.isValidKey(namespace) == false){
			throw new Error("Invalid namespace given: " + namespace);
		}
		
		// create a regular expression to test the beginning
		// of our key names to see if they match our namespace;
		// if it is the default namespace then test for the presence
		// of no namespace for compatibility with older versions
		// of dojox.storage
		var namespaceTester;
		if(namespace == this.DEFAULT_NAMESPACE){
			namespaceTester = new RegExp("^([^_]{2}.*)$");	
		}else{
			namespaceTester = new RegExp("^__" + namespace + "_(.*)$");
		}
		
		var myStorage = globalStorage[this._domain];
		var keysArray = [];
		for(var i = 0; i < myStorage.length; i++){
			var currentKey = myStorage.key(i);
			if(namespaceTester.test(currentKey) == true){
				// strip off the namespace portion
				currentKey = currentKey.match(namespaceTester)[1];
				keysArray.push(currentKey);
			}
		}
		
		return keysArray;
	},

	clear: function(namespace){
		namespace = namespace||this.DEFAULT_NAMESPACE;
		
		if(this.isValidKey(namespace) == false){
			throw new Error("Invalid namespace given: " + namespace);
		}
		
		// create a regular expression to test the beginning
		// of our key names to see if they match our namespace;
		// if it is the default namespace then test for the presence
		// of no namespace for compatibility with older versions
		// of dojox.storage
		var namespaceTester;
		if(namespace == this.DEFAULT_NAMESPACE){
			namespaceTester = new RegExp("^[^_]{2}");	
		}else{
			namespaceTester = new RegExp("^__" + namespace + "_");
		}
		
		var myStorage = globalStorage[this._domain];
		var keys = [];
		for(var i = 0; i < myStorage.length; i++){
			if(namespaceTester.test(myStorage.key(i)) == true){
				keys[keys.length] = myStorage.key(i);
			}
		}
		
		dojo.forEach(keys, dojo.hitch(myStorage, "removeItem"));
	},
	
	remove: function(key, namespace){
		// get our full key name, which is namespace + key
		key = this.getFullKey(key, namespace);
		
		var myStorage = globalStorage[this._domain];
		myStorage.removeItem(key);
	},
	
	isPermanent: function(){
		return true;
	},

	getMaximumSize: function(){
		return this.SIZE_NO_LIMIT;
	},

	hasSettingsUI: function(){
		return false;
	},
	
	showSettingsUI: function(){
		throw new Error(this.declaredClass + " does not support a storage settings user-interface");
	},
	
	hideSettingsUI: function(){
		throw new Error(this.declaredClass + " does not support a storage settings user-interface");
	},
	
	getFullKey: function(key, namespace){
		namespace = namespace||this.DEFAULT_NAMESPACE;
		
		if(this.isValidKey(namespace) == false){
			throw new Error("Invalid namespace given: " + namespace);
		}
		
		// don't append a namespace string for the default namespace,
		// for compatibility with older versions of dojox.storage
		if(namespace == this.DEFAULT_NAMESPACE){
			return key;
		}else{
			return "__" + namespace + "_" + key;
		}
	},

	_getDomain: function(){
		// see: https://bugzilla.mozilla.org/show_bug.cgi?id=357323
		return ((location.hostname == "localhost" && dojo.isFF && dojo.isFF < 3) ? "localhost.localdomain" : location.hostname);
	}
});

dojox.storage.manager.register("dojox.storage.WhatWGStorageProvider", 
								new dojox.storage.WhatWGStorageProvider());

}

if(!dojo._hasResource["dojo.AdapterRegistry"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojo.AdapterRegistry"] = true;
dojo.provide("dojo.AdapterRegistry");

dojo.AdapterRegistry = function(/*Boolean?*/ returnWrappers){
	//	summary:
	//		A registry to make contextual calling/searching easier.
	//	description:
	//		Objects of this class keep list of arrays in the form [name, check,
	//		wrap, directReturn] that are used to determine what the contextual
	//		result of a set of checked arguments is. All check/wrap functions
	//		in this registry should be of the same arity.
	//	example:
	//	|	// create a new registry
	//	|	var reg = new dojo.AdapterRegistry();
	//	|	reg.register("handleString",
	//	|		dojo.isString,
	//	|		function(str){
	//	|			// do something with the string here
	//	|		}
	//	|	);
	//	|	reg.register("handleArr",
	//	|		dojo.isArray,
	//	|		function(arr){
	//	|			// do something with the array here
	//	|		}
	//	|	);
	//	|
	//	|	// now we can pass reg.match() *either* an array or a string and
	//	|	// the value we pass will get handled by the right function
	//	|	reg.match("someValue"); // will call the first function
	//	|	reg.match(["someValue"]); // will call the second

	this.pairs = [];
	this.returnWrappers = returnWrappers || false; // Boolean
}

dojo.extend(dojo.AdapterRegistry, {
	register: function(/*String*/ name, /*Function*/ check, /*Function*/ wrap, /*Boolean?*/ directReturn, /*Boolean?*/ override){
		//	summary: 
		//		register a check function to determine if the wrap function or
		//		object gets selected
		//	name:
		//		a way to identify this matcher.
		//	check:
		//		a function that arguments are passed to from the adapter's
		//		match() function.  The check function should return true if the
		//		given arguments are appropriate for the wrap function.
		//	directReturn:
		//		If directReturn is true, the value passed in for wrap will be
		//		returned instead of being called. Alternately, the
		//		AdapterRegistry can be set globally to "return not call" using
		//		the returnWrappers property. Either way, this behavior allows
		//		the registry to act as a "search" function instead of a
		//		function interception library.
		//	override:
		//		If override is given and true, the check function will be given
		//		highest priority. Otherwise, it will be the lowest priority
		//		adapter.
		this.pairs[((override) ? "unshift" : "push")]([name, check, wrap, directReturn]);
	},

	match: function(/* ... */){
		// summary:
		//		Find an adapter for the given arguments. If no suitable adapter
		//		is found, throws an exception. match() accepts any number of
		//		arguments, all of which are passed to all matching functions
		//		from the registered pairs.
		for(var i = 0; i < this.pairs.length; i++){
			var pair = this.pairs[i];
			if(pair[1].apply(this, arguments)){
				if((pair[3])||(this.returnWrappers)){
					return pair[2];
				}else{
					return pair[2].apply(this, arguments);
				}
			}
		}
		throw new Error("No match found");
	},

	unregister: function(name){
		// summary: Remove a named adapter from the registry

		// FIXME: this is kind of a dumb way to handle this. On a large
		// registry this will be slow-ish and we can use the name as a lookup
		// should we choose to trade memory for speed.
		for(var i = 0; i < this.pairs.length; i++){
			var pair = this.pairs[i];
			if(pair[0] == name){
				this.pairs.splice(i, 1);
				return true;
			}
		}
		return false;
	}
});

}

if(!dojo._hasResource["dijit._base.place"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dijit._base.place"] = true;
dojo.provide("dijit._base.place");



// ported from dojo.html.util

dijit.getViewport = function(){
	// summary:
	//		Returns the dimensions and scroll position of the viewable area of a browser window

	var scrollRoot = (dojo.doc.compatMode == 'BackCompat')? dojo.body() : dojo.doc.documentElement;

	// get scroll position
	var scroll = dojo._docScroll(); // scrollRoot.scrollTop/Left should work
	return { w: scrollRoot.clientWidth, h: scrollRoot.clientHeight, l: scroll.x, t: scroll.y };
};

/*=====
dijit.__Position = function(){
	// x: Integer
	//		horizontal coordinate in pixels, relative to document body
	// y: Integer
	//		vertical coordinate in pixels, relative to document body

	thix.x = x;
	this.y = y;
}
=====*/


dijit.placeOnScreen = function(
	/* DomNode */			node,
	/* dijit.__Position */	pos,
	/* String[] */			corners,
	/* dijit.__Position? */	padding){
	//	summary:
	//		Positions one of the node's corners at specified position
	//		such that node is fully visible in viewport.
	//	description:
	//		NOTE: node is assumed to be absolutely or relatively positioned.
	//	pos:
	//		Object like {x: 10, y: 20}
	//	corners:
	//		Array of Strings representing order to try corners in, like ["TR", "BL"].
	//		Possible values are:
	//			* "BL" - bottom left
	//			* "BR" - bottom right
	//			* "TL" - top left
	//			* "TR" - top right
	//	padding:
	//		set padding to put some buffer around the element you want to position.
	//	example:	
	//		Try to place node's top right corner at (10,20).
	//		If that makes node go (partially) off screen, then try placing
	//		bottom left corner at (10,20).
	//	|	placeOnScreen(node, {x: 10, y: 20}, ["TR", "BL"])

	var choices = dojo.map(corners, function(corner){
		var c = { corner: corner, pos: {x:pos.x,y:pos.y} };
		if(padding){
			c.pos.x += corner.charAt(1) == 'L' ? padding.x : -padding.x;
			c.pos.y += corner.charAt(0) == 'T' ? padding.y : -padding.y;
		}
		return c; 
	});

	return dijit._place(node, choices);
}

dijit._place = function(/*DomNode*/ node, /* Array */ choices, /* Function */ layoutNode){
	// summary:
	//		Given a list of spots to put node, put it at the first spot where it fits,
	//		of if it doesn't fit anywhere then the place with the least overflow
	// choices: Array
	//		Array of elements like: {corner: 'TL', pos: {x: 10, y: 20} }
	//		Above example says to put the top-left corner of the node at (10,20)
	// layoutNode: Function(node, aroundNodeCorner, nodeCorner)
	//		for things like tooltip, they are displayed differently (and have different dimensions)
	//		based on their orientation relative to the parent.   This adjusts the popup based on orientation.

	// get {x: 10, y: 10, w: 100, h:100} type obj representing position of
	// viewport over document
	var view = dijit.getViewport();

	// This won't work if the node is inside a <div style="position: relative">,
	// so reattach it to dojo.doc.body.   (Otherwise, the positioning will be wrong
	// and also it might get cutoff)
	if(!node.parentNode || String(node.parentNode.tagName).toLowerCase() != "body"){
		dojo.body().appendChild(node);
	}

	var best = null;
	dojo.some(choices, function(choice){
		var corner = choice.corner;
		var pos = choice.pos;

		// configure node to be displayed in given position relative to button
		// (need to do this in order to get an accurate size for the node, because
		// a tooltips size changes based on position, due to triangle)
		if(layoutNode){
			layoutNode(node, choice.aroundCorner, corner);
		}

		// get node's size
		var style = node.style;
		var oldDisplay = style.display;
		var oldVis = style.visibility;
		style.visibility = "hidden";
		style.display = "";
		var mb = dojo.marginBox(node);
		style.display = oldDisplay;
		style.visibility = oldVis;

		// coordinates and size of node with specified corner placed at pos,
		// and clipped by viewport
		var startX = (corner.charAt(1) == 'L' ? pos.x : Math.max(view.l, pos.x - mb.w)),
			startY = (corner.charAt(0) == 'T' ? pos.y : Math.max(view.t, pos.y -  mb.h)),
			endX = (corner.charAt(1) == 'L' ? Math.min(view.l + view.w, startX + mb.w) : pos.x),
			endY = (corner.charAt(0) == 'T' ? Math.min(view.t + view.h, startY + mb.h) : pos.y),
			width = endX - startX,
			height = endY - startY,
			overflow = (mb.w - width) + (mb.h - height);

		if(best == null || overflow < best.overflow){
			best = {
				corner: corner,
				aroundCorner: choice.aroundCorner,
				x: startX,
				y: startY,
				w: width,
				h: height,
				overflow: overflow
			};
		}
		return !overflow;
	});

	node.style.left = best.x + "px";
	node.style.top = best.y + "px";
	if(best.overflow && layoutNode){
		layoutNode(node, best.aroundCorner, best.corner);
	}
	return best;
}

dijit.placeOnScreenAroundNode = function(
	/* DomNode */		node,
	/* DomNode */		aroundNode,
	/* Object */		aroundCorners,
	/* Function? */		layoutNode){

	// summary:
	//		Position node adjacent or kitty-corner to aroundNode
	//		such that it's fully visible in viewport.
	//
	// description:
	//		Place node such that corner of node touches a corner of
	//		aroundNode, and that node is fully visible.
	//
	// aroundCorners:
	//		Ordered list of pairs of corners to try matching up.
	//		Each pair of corners is represented as a key/value in the hash,
	//		where the key corresponds to the aroundNode's corner, and
	//		the value corresponds to the node's corner:
	//
	//	|	{ aroundNodeCorner1: nodeCorner1, aroundNodeCorner2: nodeCorner2,  ...}
	//
	//		The following strings are used to represent the four corners:
	//			* "BL" - bottom left
	//			* "BR" - bottom right
	//			* "TL" - top left
	//			* "TR" - top right
	//
	// layoutNode: Function(node, aroundNodeCorner, nodeCorner)
	//		For things like tooltip, they are displayed differently (and have different dimensions)
	//		based on their orientation relative to the parent.   This adjusts the popup based on orientation.
	//
	// example:
	//	|	dijit.placeOnScreenAroundNode(node, aroundNode, {'BL':'TL', 'TR':'BR'}); 
	//		This will try to position node such that node's top-left corner is at the same position
	//		as the bottom left corner of the aroundNode (ie, put node below
	//		aroundNode, with left edges aligned).  If that fails it will try to put
	// 		the bottom-right corner of node where the top right corner of aroundNode is
	//		(ie, put node above aroundNode, with right edges aligned)
	//

	// get coordinates of aroundNode
	aroundNode = dojo.byId(aroundNode);
	var oldDisplay = aroundNode.style.display;
	aroundNode.style.display="";
	// #3172: use the slightly tighter border box instead of marginBox
	var aroundNodeW = aroundNode.offsetWidth; //mb.w; 
	var aroundNodeH = aroundNode.offsetHeight; //mb.h;
	var aroundNodePos = dojo.coords(aroundNode, true);
	aroundNode.style.display=oldDisplay;

	// place the node around the calculated rectangle
	return dijit._placeOnScreenAroundRect(node, 
		aroundNodePos.x, aroundNodePos.y, aroundNodeW, aroundNodeH,	// rectangle
		aroundCorners, layoutNode);
};

/*=====
dijit.__Rectangle = function(){
	// x: Integer
	//		horizontal offset in pixels, relative to document body
	// y: Integer
	//		vertical offset in pixels, relative to document body
	// width: Integer
	//		width in pixels
	// height: Integer
	//		height in pixels

	thix.x = x;
	this.y = y;
	thix.width = width;
	this.height = height;
}
=====*/


dijit.placeOnScreenAroundRectangle = function(
	/* DomNode */			node,
	/* dijit.__Rectangle */	aroundRect,
	/* Object */			aroundCorners,
	/* Function */			layoutNode){

	// summary:
	//		Like dijit.placeOnScreenAroundNode(), except that the "around"
	//		parameter is an arbitrary rectangle on the screen (x, y, width, height)
	//		instead of a dom node.

	return dijit._placeOnScreenAroundRect(node, 
		aroundRect.x, aroundRect.y, aroundRect.width, aroundRect.height,	// rectangle
		aroundCorners, layoutNode);
};

dijit._placeOnScreenAroundRect = function(
	/* DomNode */		node,
	/* Number */		x,
	/* Number */		y,
	/* Number */		width,
	/* Number */		height,
	/* Object */		aroundCorners,
	/* Function */		layoutNode){

	// summary:
	//		Like dijit.placeOnScreenAroundNode(), except it accepts coordinates
	//		of a rectangle to place node adjacent to.

	// TODO: combine with placeOnScreenAroundRectangle()

	// Generate list of possible positions for node
	var choices = [];
	for(var nodeCorner in aroundCorners){
		choices.push( {
			aroundCorner: nodeCorner,
			corner: aroundCorners[nodeCorner],
			pos: {
				x: x + (nodeCorner.charAt(1) == 'L' ? 0 : width),
				y: y + (nodeCorner.charAt(0) == 'T' ? 0 : height)
			}
		});
	}

	return dijit._place(node, choices, layoutNode);
};

dijit.placementRegistry = new dojo.AdapterRegistry();
dijit.placementRegistry.register("node",
	function(n, x){
		return typeof x == "object" &&
			typeof x.offsetWidth != "undefined" && typeof x.offsetHeight != "undefined";
	},
	dijit.placeOnScreenAroundNode);
dijit.placementRegistry.register("rect",
	function(n, x){
		return typeof x == "object" &&
			"x" in x && "y" in x && "width" in x && "height" in x;
	},
	dijit.placeOnScreenAroundRectangle);

dijit.placeOnScreenAroundElement = function(
	/* DomNode */		node,
	/* Object */		aroundElement,
	/* Object */		aroundCorners,
	/* Function */		layoutNode){

	// summary:
	//		Like dijit.placeOnScreenAroundNode(), except it accepts an arbitrary object
	//		for the "around" argument and finds a proper processor to place a node.

	return dijit.placementRegistry.match.apply(dijit.placementRegistry, arguments);
};

}

if(!dojo._hasResource["dojox.flash._base"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.flash._base"] = true;
dojo.provide("dojox.flash._base");
dojo.experimental("dojox.flash");

// for dijit.getViewport(), needed by dojox.flash.Embed.center()


dojox.flash = function(){
	// summary:
	//	Utilities to embed and communicate with the Flash player from Javascript
	//
	// description:
	//	The goal of dojox.flash is to make it easy to extend Flash's capabilities
	//	into an Ajax/DHTML environment.
	//  
	//	dojox.flash provides an easy object for interacting with the Flash plugin. 
	//	This object provides methods to determine the current version of the Flash
	//	plugin (dojox.flash.info); write out the necessary markup to 
	//	dynamically insert a Flash object into the page (dojox.flash.Embed; and 
	//	do dynamic installation and upgrading of the current Flash plugin in 
	//	use (dojox.flash.Install). If you want to call methods on the Flash object
	//	embedded into the page it is your responsibility to use Flash's ExternalInterface
	//	API and get a reference to the Flash object yourself.
	//		
	//	To use dojox.flash, you must first wait until Flash is finished loading 
	//	and initializing before you attempt communication or interaction. 
	//	To know when Flash is finished use dojo.connect:
	//		
	//|	dojo.connect(dojox.flash, "loaded", myInstance, "myCallback");
	//		
	//	Then, while the page is still loading provide the file name:
	//		
	//|	dojox.flash.setSwf(dojo.moduleUrl("dojox", "_storage/storage.swf"));
	//			
	//	If no SWF files are specified, then Flash is not initialized.
	//		
	//	Your Flash must use Flash's ExternalInterface to expose Flash methods and
	//	to call JavaScript.
	//		
	//	setSwf can take an optional 'visible' attribute to control whether
	//	the Flash object is visible or not on the page; the default is visible:
	//		
	//|	dojox.flash.setSwf(dojo.moduleUrl("dojox", "_storage/storage.swf"),
	//						false);
	//		
	//	Once finished, you can query Flash version information:
	//		
	//|	dojox.flash.info.version
	//		
	//	Or can communicate with Flash methods that were exposed:	
	//
	//|	var f = dojox.flash.get();
	//|	var results = f.sayHello("Some Message");	
	// 
	//	Your Flash files should use DojoExternalInterface.as to register methods;
	//	this file wraps Flash's normal ExternalInterface but correct various
	//	serialization bugs that ExternalInterface has.
	//
	//	Note that dojox.flash is not meant to be a generic Flash embedding
	//	mechanism; it is as generic as necessary to make Dojo Storage's
	//	Flash Storage Provider as clean and modular as possible. If you want 
	//	a generic Flash embed mechanism see [SWFObject](http://blog.deconcept.com/swfobject/).
	//
	// 	Notes:
	//	Note that dojox.flash can currently only work with one Flash object
	//	on the page; it does not yet support multiple Flash objects on
	//	the same page. 
	//		
	//	Your code can detect whether the Flash player is installing or having
	//	its version revved in two ways. First, if dojox.flash detects that
	//	Flash installation needs to occur, it sets dojox.flash.info.installing
	//	to true. Second, you can detect if installation is necessary with the
	//	following callback:
	//		
	//|	dojo.connect(dojox.flash, "installing", myInstance, "myCallback");
	//		
	//	You can use this callback to delay further actions that might need Flash;
	//	when installation is finished the full page will be refreshed and the
	//	user will be placed back on your page with Flash installed.
	//		
	//	-------------------
	//	Todo/Known Issues
	//	-------------------
	//
	//	* On Internet Explorer, after doing a basic install, the page is
	//	not refreshed or does not detect that Flash is now available. The way
	//	to fix this is to create a custom small Flash file that is pointed to
	//	during installation; when it is finished loading, it does a callback
	//	that says that Flash installation is complete on IE, and we can proceed
	//	to initialize the dojox.flash subsystem.
	//	* Things aren't super tested for sending complex objects to Flash
	//	methods, since Dojo Storage only needs strings
	//		
	//	Author- Brad Neuberg, http://codinginparadise.org
}

dojox.flash = {
	ready: false,
	url: null,
	
	_visible: true,
	_loadedListeners: [],
	_installingListeners: [],
	
	setSwf: function(/* String */ url, /* boolean? */ visible){
		// summary: Sets the SWF files and versions we are using.
		// url: String
		//	The URL to this Flash file.
		// visible: boolean?
		//	Whether the Flash file is visible or not. If it is not visible we hide 
		//	it off the screen. This defaults to true (i.e. the Flash file is
		//	visible).
		this.url = url;
		
		this._visible = true;
		if(visible !== null && visible !== undefined){
			this._visible = visible;
		}
		
		// initialize ourselves		
		this._initialize();
	},
	
	addLoadedListener: function(/* Function */ listener){
		// summary:
		//	Adds a listener to know when Flash is finished loading. 
		//	Useful if you don't want a dependency on dojo.event.
		// listener: Function
		//	A function that will be called when Flash is done loading.
		
		this._loadedListeners.push(listener);
	},

	addInstallingListener: function(/* Function */ listener){
		// summary:
		//	Adds a listener to know if Flash is being installed. 
		//	Useful if you don't want a dependency on dojo.event.
		// listener: Function
		//	A function that will be called if Flash is being
		//	installed
		
		this._installingListeners.push(listener);
	},	
	
	loaded: function(){
		// summary: Called back when the Flash subsystem is finished loading.
		// description:
		//	A callback when the Flash subsystem is finished loading and can be
		//	worked with. To be notified when Flash is finished loading, add a
		//  loaded listener: 
		//
		//  dojox.flash.addLoadedListener(loadedListener);
	
		dojox.flash.ready = true;
		if(dojox.flash._loadedListeners.length){ // FIXME: redundant if? use forEach?
			for(var i = 0;i < dojox.flash._loadedListeners.length; i++){
				dojox.flash._loadedListeners[i].call(null);
			}
		}
	},
	
	installing: function(){
		// summary: Called if Flash is being installed.
		// description:
		//	A callback to know if Flash is currently being installed or
		//	having its version revved. To be notified if Flash is installing, connect
		//	your callback to this method using the following:
		//	
		//	dojo.event.connect(dojox.flash, "installing", myInstance, "myCallback");
		
		if(dojox.flash._installingListeners.length){ // FIXME: redundant if? use forEach?
			for(var i = 0; i < dojox.flash._installingListeners.length; i++){
				dojox.flash._installingListeners[i].call(null);
			}
		}
	},
	
	// Initializes dojox.flash.
	_initialize: function(){
		//
		// see if we need to rev or install Flash on this platform
		var installer = new dojox.flash.Install();
		dojox.flash.installer = installer;

		if(installer.needed()){		
			installer.install();
		}else{
			// write the flash object into the page
			dojox.flash.obj = new dojox.flash.Embed(this._visible);
			dojox.flash.obj.write();
			
			// setup the communicator
			dojox.flash.comm = new dojox.flash.Communicator();
		}
	}
};


dojox.flash.Info = function(){
	// summary: A class that helps us determine whether Flash is available.
	// description:
	//	A class that helps us determine whether Flash is available,
	//	it's major and minor versions, and what Flash version features should
	//	be used for Flash/JavaScript communication. Parts of this code
	//	are adapted from the automatic Flash plugin detection code autogenerated 
	//	by the Macromedia Flash 8 authoring environment. 
	//	
	//	An instance of this class can be accessed on dojox.flash.info after
	//	the page is finished loading.

	this._detectVersion();
}

dojox.flash.Info.prototype = {
	// version: String
	//		The full version string, such as "8r22".
	version: -1,
	
	// versionMajor, versionMinor, versionRevision: String
	//		The major, minor, and revisions of the plugin. For example, if the
	//		plugin is 8r22, then the major version is 8, the minor version is 0,
	//		and the revision is 22. 
	versionMajor: -1,
	versionMinor: -1,
	versionRevision: -1,
	
	// capable: Boolean
	//		Whether this platform has Flash already installed.
	capable: false,
	
	// installing: Boolean
	//	Set if we are in the middle of a Flash installation session.
	installing: false,
	
	isVersionOrAbove: function(
							/* int */ reqMajorVer, 
							/* int */ reqMinorVer, 
							/* int */ reqVer){ /* Boolean */
		// summary: 
		//	Asserts that this environment has the given major, minor, and revision
		//	numbers for the Flash player.
		// description:
		//	Asserts that this environment has the given major, minor, and revision
		//	numbers for the Flash player. 
		//	
		//	Example- To test for Flash Player 7r14:
		//	
		//	dojox.flash.info.isVersionOrAbove(7, 0, 14)
		// returns:
		//	Returns true if the player is equal
		//	or above the given version, false otherwise.
		
		// make the revision a decimal (i.e. transform revision 14 into
		// 0.14
		reqVer = parseFloat("." + reqVer);
		
		if(this.versionMajor >= reqMajorVer && this.versionMinor >= reqMinorVer
			 && this.versionRevision >= reqVer){
			return true;
		}else{
			return false;
		}
	},
	
	_detectVersion: function(){
		var versionStr;
		
		// loop backwards through the versions until we find the newest version	
		for(var testVersion = 25; testVersion > 0; testVersion--){
			if(dojo.isIE){
				var axo;
				try{
					if(testVersion > 6){
						axo = new ActiveXObject("ShockwaveFlash.ShockwaveFlash." 
																		+ testVersion);
					}else{
						axo = new ActiveXObject("ShockwaveFlash.ShockwaveFlash");
					}
					if(typeof axo == "object"){
						if(testVersion == 6){
							axo.AllowScriptAccess = "always";
						}
						versionStr = axo.GetVariable("$version");
					}
				}catch(e){
					continue;
				}
			}else{
				versionStr = this._JSFlashInfo(testVersion);		
			}
				
			if(versionStr == -1 ){
				this.capable = false; 
				return;
			}else if(versionStr != 0){
				var versionArray;
				if(dojo.isIE){
					var tempArray = versionStr.split(" ");
					var tempString = tempArray[1];
					versionArray = tempString.split(",");
				}else{
					versionArray = versionStr.split(".");
				}
					
				this.versionMajor = versionArray[0];
				this.versionMinor = versionArray[1];
				this.versionRevision = versionArray[2];
				
				// 7.0r24 == 7.24
				var versionString = this.versionMajor + "." + this.versionRevision;
				this.version = parseFloat(versionString);
				
				this.capable = true;
				
				break;
			}
		}
	},
	 
	// JavaScript helper required to detect Flash Player PlugIn version 
	// information. Internet Explorer uses a corresponding Visual Basic
	// version to interact with the Flash ActiveX control. 
	_JSFlashInfo: function(testVersion){
		// NS/Opera version >= 3 check for Flash plugin in plugin array
		if(navigator.plugins != null && navigator.plugins.length > 0){
			if(navigator.plugins["Shockwave Flash 2.0"] || 
				 navigator.plugins["Shockwave Flash"]){
				var swVer2 = navigator.plugins["Shockwave Flash 2.0"] ? " 2.0" : "";
				var flashDescription = navigator.plugins["Shockwave Flash" + swVer2].description;
				var descArray = flashDescription.split(" ");
				var tempArrayMajor = descArray[2].split(".");
				var versionMajor = tempArrayMajor[0];
				var versionMinor = tempArrayMajor[1];
				var tempArrayMinor = (descArray[3] || descArray[4]).split("r");
				var versionRevision = tempArrayMinor[1] > 0 ? tempArrayMinor[1] : 0;
				var version = versionMajor + "." + versionMinor + "." + versionRevision;
											
				return version;
			}
		}
		
		return -1;
	}
};

dojox.flash.Embed = function(visible){
	// summary: A class that is used to write out the Flash object into the page.
	// description:
	//	Writes out the necessary tags to embed a Flash file into the page. Note that
	//	these tags are written out as the page is loaded using document.write, so
	//	you must call this class before the page has finished loading.
	
	this._visible = visible;
}

dojox.flash.Embed.prototype = {
	// width: int
	//	The width of this Flash applet. The default is the minimal width
	//	necessary to show the Flash settings dialog. Current value is 
	//  215 pixels.
	width: 215,
	
	// height: int 
	//	The height of this Flash applet. The default is the minimal height
	//	necessary to show the Flash settings dialog. Current value is
	// 138 pixels.
	height: 138,
	
	// id: String
	// 	The id of the Flash object. Current value is 'flashObject'.
	id: "flashObject",
	
	// Controls whether this is a visible Flash applet or not.
	_visible: true,

	protocol: function(){
		switch(window.location.protocol){
			case "https:":
				return "https";
				break;
			default:
				return "http";
				break;
		}
	},
	
	write: function(/* Boolean? */ doExpressInstall){
		// summary: Writes the Flash into the page.
		// description:
		//	This must be called before the page
		//	is finished loading. 
		// doExpressInstall: Boolean
		//	Whether to write out Express Install
		//	information. Optional value; defaults to false.
		
		// figure out the SWF file to get and how to write out the correct HTML
		// for this Flash version
		var objectHTML;
		var swfloc = dojox.flash.url;
		var swflocObject = swfloc;
		var swflocEmbed = swfloc;
		var dojoUrl = dojo.baseUrl;
		var xdomainBase = document.location.protocol + '//' + document.location.host;
		if(doExpressInstall){
			// the location to redirect to after installing
			var redirectURL = escape(window.location);
			document.title = document.title.slice(0, 47) + " - Flash Player Installation";
			var docTitle = escape(document.title);
			swflocObject += "?MMredirectURL=" + redirectURL
			                + "&MMplayerType=ActiveX"
			                + "&MMdoctitle=" + docTitle
			                + "&baseUrl=" + escape(dojoUrl)
			                + "&xdomain=" + escape(xdomainBase);
			swflocEmbed += "?MMredirectURL=" + redirectURL 
			                + "&MMplayerType=PlugIn"
			                + "&baseUrl=" + escape(dojoUrl)
			                + "&xdomain=" + escape(xdomainBase);
		}else{
			// IE/Flash has an evil bug that shows up some time: if we load the
			// Flash and it isn't in the cache, ExternalInterface works fine --
			// however, the second time when its loaded from the cache a timing
			// bug can keep ExternalInterface from working. The trick below 
			// simply invalidates the Flash object in the cache all the time to
			// keep it loading fresh. -- Brad Neuberg
			swflocObject += "?cachebust=" + new Date().getTime();
			swflocObject += "&baseUrl=" + escape(dojoUrl);
			swflocObject += "&xdomain=" + escape(xdomainBase);
		}

		if(swflocEmbed.indexOf("?") == -1){
			swflocEmbed += '?baseUrl='+escape(dojoUrl);
		}else{
		  swflocEmbed += '&baseUrl='+escape(dojoUrl);
		}
		swflocEmbed += '&xdomain='+escape(xdomainBase);
		
		objectHTML =
			'<object classid="clsid:d27cdb6e-ae6d-11cf-96b8-444553540000" '
			  + 'codebase="'
				+ this.protocol()
				+ '://fpdownload.macromedia.com/pub/shockwave/cabs/flash/'
				+ 'swflash.cab#version=8,0,0,0"\n '
			  + 'width="' + this.width + '"\n '
			  + 'height="' + this.height + '"\n '
			  + 'id="' + this.id + '"\n '
			  + 'name="' + this.id + '"\n '
			  + 'align="middle">\n '
			  + '<param name="allowScriptAccess" value="always"></param>\n '
			  + '<param name="movie" value="' + swflocObject + '"></param>\n '
			  + '<param name="quality" value="high"></param>\n '
			  + '<param name="bgcolor" value="#ffffff"></param>\n '
			  + '<embed src="' + swflocEmbed + '" '
			  	  + 'quality="high" '
				  + 'bgcolor="#ffffff" '
				  + 'width="' + this.width + '" '
				  + 'height="' + this.height + '" '
				  + 'id="' + this.id + 'Embed' + '" '
				  + 'name="' + this.id + '" '
				  + 'swLiveConnect="true" '
				  + 'align="middle" '
				  + 'allowScriptAccess="always" '
				  + 'type="application/x-shockwave-flash" '
				  + 'pluginspage="'
				  + this.protocol()
				  +'://www.macromedia.com/go/getflashplayer" '
				  + '></embed>\n'
			+ '</object>\n';
					
		// using same mechanism on all browsers now to write out
		// Flash object into page

		// document.write no longer works correctly due to Eolas patent workaround
		// in IE; nothing happens (i.e. object doesn't go into page if we use it)
		dojo.connect(dojo, "loaded", dojo.hitch(this, function(){
			// Prevent putting duplicate SWFs onto the page
			var containerId = this.id + "Container";
			if(dojo.byId(containerId)){
				return;
			}
			
			var div = document.createElement("div");
			div.id = this.id + "Container";
			
			div.style.width = this.width + "px";
			div.style.height = this.height + "px";
			if(!this._visible){
				div.style.position = "absolute";
				div.style.zIndex = "10000";
				div.style.top = "-1000px";
			}

			div.innerHTML = objectHTML;

			var body = document.getElementsByTagName("body");
			if(!body || !body.length){
				throw new Error("No body tag for this page");
			}
			body = body[0];
			body.appendChild(div);
		}));
	},  
	
	get: function(){ /* Object */
		// summary: Gets the Flash object DOM node.

		if(dojo.isIE || dojo.isWebKit){
			//TODO: should this really be the else?
			return dojo.byId(this.id);
		}else{
			// different IDs on OBJECT and EMBED tags or
			// else Firefox will return wrong one and
			// communication won't work; 
			// also, document.getElementById() returns a
			// plugin but ExternalInterface calls don't
			// work on it so we have to use
			// document[id] instead
			return document[this.id + "Embed"];
		}
	},
	
	setVisible: function(/* Boolean */ visible){
		//
		
		// summary: Sets the visibility of this Flash object.		
		var container = dojo.byId(this.id + "Container");
		if(visible){
			container.style.position = "absolute"; // IE -- Brad Neuberg
			container.style.visibility = "visible";
		}else{
			container.style.position = "absolute";
			container.style.y = "-1000px";
			container.style.visibility = "hidden";
		}
	},
	
	center: function(){
		// summary: Centers the flash applet on the page.
		
		var elementWidth = this.width;
		var elementHeight = this.height;

		var viewport = dijit.getViewport();

		// compute the centered position    
		var x = viewport.l + (viewport.w - elementWidth) / 2;
		var y = viewport.t + (viewport.h - elementHeight) / 2; 
		
		// set the centered position
		var container = dojo.byId(this.id + "Container");
		container.style.top = y + "px";
		container.style.left = x + "px";
	}
};


dojox.flash.Communicator = function(){
	// summary:
	//	A class that is used to communicate between Flash and JavaScript.
	// description:
	//	This class helps mediate Flash and JavaScript communication. Internally
	//	it uses Flash 8's ExternalInterface API, but adds functionality to fix 
	//	various encoding bugs that ExternalInterface has.
}

dojox.flash.Communicator.prototype = {
	// Registers the existence of a Flash method that we can call with
	// JavaScript, using Flash 8's ExternalInterface. 
	_addExternalInterfaceCallback: function(methodName){
		//
		var wrapperCall = dojo.hitch(this, function(){
			// some browsers don't like us changing values in the 'arguments' array, so
			// make a fresh copy of it
			var methodArgs = new Array(arguments.length);
			for(var i = 0; i < arguments.length; i++){
				methodArgs[i] = this._encodeData(arguments[i]);
			}
			
			var results = this._execFlash(methodName, methodArgs);
			results = this._decodeData(results);
			
			return results;
		});
		
		this[methodName] = wrapperCall;
	},
	
	// Encodes our data to get around ExternalInterface bugs that are still
	// present even in Flash 9.
	_encodeData: function(data){
		//
		if(!data || typeof data != "string"){
			return data;
		}
		
		// transforming \ into \\ doesn't work; just use a custom encoding
		data = data.replace("\\", "&custom_backslash;");

		// also use custom encoding for the null character to avoid problems 
		data = data.replace(/\0/g, "&custom_null;");

		return data;
	},
	
	// Decodes our data to get around ExternalInterface bugs that are still
	// present even in Flash 9.
	_decodeData: function(data){
		//
		// wierdly enough, Flash sometimes returns the result as an
		// 'object' that is actually an array, rather than as a String;
		// detect this by looking for a length property; for IE
		// we also make sure that we aren't dealing with a typeof string
		// since string objects have length property there
		if(data && data.length && typeof data != "string"){
			data = data[0];
		}
		
		if(!data || typeof data != "string"){
			return data;
		}
		
		// needed for IE; \0 is the NULL character 
		data = data.replace(/\&custom_null\;/g, "\0");
	
		// certain XMLish characters break Flash's wire serialization for
		// ExternalInterface; these are encoded on the 
		// DojoExternalInterface side into a custom encoding, rather than
		// the standard entity encoding, because otherwise we won't be able to
		// differentiate between our own encoding and any entity characters
		// that are being used in the string itself
		data = data.replace(/\&custom_lt\;/g, "<")
			.replace(/\&custom_gt\;/g, ">")
			.replace(/\&custom_backslash\;/g, '\\');
		
		return data;
	},
	
	// Executes a Flash method; called from the JavaScript wrapper proxy we
	// create on dojox.flash.comm.
	_execFlash: function(methodName, methodArgs){
		//
		var plugin = dojox.flash.obj.get();
		methodArgs = (methodArgs) ? methodArgs : [];
		
		// encode arguments that are strings
		for(var i = 0; i < methodArgs; i++){
			if(typeof methodArgs[i] == "string"){
				methodArgs[i] = this._encodeData(methodArgs[i]);
			}
		}

		// we use this gnarly hack below instead of 
		// plugin[methodName] for two reasons:
		// 1) plugin[methodName] has no call() method, which
		// means we can't pass in multiple arguments dynamically
		// to a Flash method -- we can only have one
		// 2) On IE plugin[methodName] returns undefined -- 
		// plugin[methodName] used to work on IE when we
		// used document.write but doesn't now that
		// we use dynamic DOM insertion of the Flash object
		// -- Brad Neuberg
		var flashExec = function(){ 
			return eval(plugin.CallFunction(
						 "<invoke name=\"" + methodName
						+ "\" returntype=\"javascript\">" 
						+ __flash__argumentsToXML(methodArgs, 0) 
						+ "</invoke>")); 
		};
		var results = flashExec.call(methodArgs);
		
		if(typeof results == "string"){
			results = this._decodeData(results);
		}
			
		return results;
	}
}

// FIXME: dojo.declare()-ify this

// TODO: I did not test the Install code when I refactored Dojo Flash from 0.4 to 
// 1.0, so am not sure if it works. If Flash is not present I now prefer 
// that Gears is installed instead of Flash because GearsStorageProvider is
// much easier to work with than Flash's hacky ExternalInteface. 
// -- Brad Neuberg
dojox.flash.Install = function(){
	// summary: Helps install Flash plugin if needed.
	// description:
	//		Figures out the best way to automatically install the Flash plugin
	//		for this browser and platform. Also determines if installation or
	//		revving of the current plugin is needed on this platform.
}

dojox.flash.Install.prototype = {
	needed: function(){ /* Boolean */
		// summary:
		//		Determines if installation or revving of the current plugin is
		//		needed. 
	
		// do we even have flash?
		if(!dojox.flash.info.capable){
			return true;
		}

		// Must have ExternalInterface which came in Flash 8
		if(!dojox.flash.info.isVersionOrAbove(8, 0, 0)){
			return true;
		}

		// otherwise we don't need installation
		return false;
	},

	install: function(){
		// summary: Performs installation or revving of the Flash plugin.
		var installObj;
	
		// indicate that we are installing
		dojox.flash.info.installing = true;
		dojox.flash.installing();
		
		if(dojox.flash.info.capable == false){ // we have no Flash at all
			// write out a simple Flash object to force the browser to prompt
			// the user to install things
			installObj = new dojox.flash.Embed(false);
			installObj.write(); // write out HTML for Flash
		}else if(dojox.flash.info.isVersionOrAbove(6, 0, 65)){ // Express Install
			installObj = new dojox.flash.Embed(false);
			installObj.write(true); // write out HTML for Flash 8 version+
			installObj.setVisible(true);
			installObj.center();
		}else{ // older Flash install than version 6r65
			alert("This content requires a more recent version of the Macromedia "
						+" Flash Player.");
			window.location.href = + dojox.flash.Embed.protocol() +
						"://www.macromedia.com/go/getflashplayer";
		}
	},
	
	// Called when the Express Install is either finished, failed, or was
	// rejected by the user.
	_onInstallStatus: function(msg){
		if (msg == "Download.Complete"){
			// Installation is complete.
			dojox.flash._initialize();
		}else if(msg == "Download.Cancelled"){
			alert("This content requires a more recent version of the Macromedia "
						+" Flash Player.");
			window.location.href = dojox.flash.Embed.protocol() +
						"://www.macromedia.com/go/getflashplayer";
		}else if (msg == "Download.Failed"){
			// The end user failed to download the installer due to a network failure
			alert("There was an error downloading the Flash Player update. "
						+ "Please try again later, or visit macromedia.com to download "
						+ "the latest version of the Flash plugin.");
		}	
	}
}

// find out if Flash is installed
dojox.flash.info = new dojox.flash.Info();

// vim:ts=4:noet:tw=0:

}

if(!dojo._hasResource["dojox.flash"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.flash"] = true;
dojo.provide("dojox.flash");


}

if(!dojo._hasResource["dojox.storage.FlashStorageProvider"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.storage.FlashStorageProvider"] = true;
dojo.provide("dojox.storage.FlashStorageProvider");





// summary: 
//		Storage provider that uses features in Flash to achieve permanent
//		storage
// description:
//		Authors of this storage provider-
//			Brad Neuberg, bkn3@columbia.edu	
dojo.declare("dojox.storage.FlashStorageProvider", dojox.storage.Provider, {
		initialized: false,
		
		_available: null,
		_statusHandler: null,
		_flashReady: false,
		_pageReady: false,
		
		initialize: function(){
		  //
			if(dojo.config["disableFlashStorage"] == true){
				return;
			}
			
			// initialize our Flash
			dojox.flash.addLoadedListener(dojo.hitch(this, function(){
			  //
			  // indicate our Flash subsystem is now loaded
			  this._flashReady = true;
			  if(this._flashReady && this._pageReady){
				  this._loaded();
				}
			}));
			var swfLoc = dojo.moduleUrl("dojox", "storage/Storage.swf").toString();
			dojox.flash.setSwf(swfLoc, false);
			
			// wait till page is finished loading
			dojo.connect(dojo, "loaded", this, function(){
			  //
			  this._pageReady = true;
			  if(this._flashReady && this._pageReady){
			    this._loaded();
			  }
			});
		},
		
		//	Set a new value for the flush delay timer.
		//	Possible values:
		//	  0 : Perform the flush synchronously after each "put" request
		//	> 0 : Wait until 'newDelay' ms have passed without any "put" request to flush
		//	 -1 : Do not  automatically flush
		setFlushDelay: function(newDelay){
			if(newDelay === null || typeof newDelay === "undefined" || isNaN(newDelay)){
				throw new Error("Invalid argunment: " + newDelay);
			}
			
			dojox.flash.comm.setFlushDelay(String(newDelay));
		},
		
		getFlushDelay: function(){
			return Number(dojox.flash.comm.getFlushDelay());
		},
		
		flush: function(namespace){
			//FIXME: is this test necessary?  Just use !namespace
			if(namespace == null || typeof namespace == "undefined"){
				namespace = dojox.storage.DEFAULT_NAMESPACE;		
			}
			dojox.flash.comm.flush(namespace);
		},

		isAvailable: function(){
			return (this._available = !dojo.config["disableFlashStorage"]);
		},

		put: function(key, value, resultsHandler, namespace){
			if(!this.isValidKey(key)){
				throw new Error("Invalid key given: " + key);
			}
			
			if(!namespace){
				namespace = dojox.storage.DEFAULT_NAMESPACE;		
			}
			
			if(!this.isValidKey(namespace)){
				throw new Error("Invalid namespace given: " + namespace);
			}
				
			this._statusHandler = resultsHandler;
			
			// serialize the value;
			// handle strings differently so they have better performance
			if(dojo.isString(value)){
				value = "string:" + value;
			}else{
				value = dojo.toJson(value);
			}
			
			dojox.flash.comm.put(key, value, namespace);
		},

		putMultiple: function(keys, values, resultsHandler, namespace){
			if(!this.isValidKeyArray(keys) || ! values instanceof Array 
			    || keys.length != values.length){
				throw new Error("Invalid arguments: keys = [" + keys + "], values = [" + values + "]");
			}
			
			if(!namespace){
				namespace = dojox.storage.DEFAULT_NAMESPACE;		
			}

			if(!this.isValidKey(namespace)){
				throw new Error("Invalid namespace given: " + namespace);
			}

			this._statusHandler = resultsHandler;
			
			//	Convert the arguments on strings we can pass along to Flash
			var metaKey = keys.join(",");
			var lengths = [];
			for(var i=0;i<values.length;i++){
				if(dojo.isString(values[i])){
					values[i] = "string:" + values[i];
				}else{
					values[i] = dojo.toJson(values[i]);
				}
				lengths[i] = values[i].length; 
			}
			var metaValue = values.join("");
			var metaLengths = lengths.join(",");
			
			dojox.flash.comm.putMultiple(metaKey, metaValue, metaLengths, namespace);
		},

		get: function(key, namespace){
			if(!this.isValidKey(key)){
				throw new Error("Invalid key given: " + key);
			}
			
			if(!namespace){
				namespace = dojox.storage.DEFAULT_NAMESPACE;		
			}
			
			if(!this.isValidKey(namespace)){
				throw new Error("Invalid namespace given: " + namespace);
			}
			
			var results = dojox.flash.comm.get(key, namespace);

			if(results == ""){
				return null;
			}
		
			return this._destringify(results);
		},

		getMultiple: function(/*array*/ keys, /*string?*/ namespace){ /*Object*/
			if(!this.isValidKeyArray(keys)){
				throw new ("Invalid key array given: " + keys);
			}
			
			if(!namespace){
				namespace = dojox.storage.DEFAULT_NAMESPACE;		
			}
			
			if(!this.isValidKey(namespace)){
				throw new Error("Invalid namespace given: " + namespace);
			}
			
			var metaKey = keys.join(",");
			var metaResults = dojox.flash.comm.getMultiple(metaKey, namespace);
			var results = eval("(" + metaResults + ")");
			
			//	destringify each entry back into a real JS object
			//FIXME: use dojo.map
			for(var i = 0; i < results.length; i++){
				results[i] = (results[i] == "") ? null : this._destringify(results[i]);
			}
			
			return results;		
		},

		_destringify: function(results){
			// destringify the content back into a 
			// real JavaScript object;
			// handle strings differently so they have better performance
			if(dojo.isString(results) && (/^string:/.test(results))){
				results = results.substring("string:".length);
			}else{
				results = dojo.fromJson(results);
			}
		
			return results;
		},
		
		getKeys: function(namespace){
			if(!namespace){
				namespace = dojox.storage.DEFAULT_NAMESPACE;		
			}
			
			if(!this.isValidKey(namespace)){
				throw new Error("Invalid namespace given: " + namespace);
			}
			
			var results = dojox.flash.comm.getKeys(namespace);
			
			// Flash incorrectly returns an empty string as "null"
			if(results == null || results == "null"){
			  results = "";
			}
			
			results = results.split(",");
			results.sort();
			
			return results;
		},
		
		getNamespaces: function(){
			var results = dojox.flash.comm.getNamespaces();
			
			// Flash incorrectly returns an empty string as "null"
			if(results == null || results == "null"){
			  results = dojox.storage.DEFAULT_NAMESPACE;
			}
			
			results = results.split(",");
			results.sort();
			
			return results;
		},

		clear: function(namespace){
			if(!namespace){
				namespace = dojox.storage.DEFAULT_NAMESPACE;
			}
			
			if(!this.isValidKey(namespace)){
				throw new Error("Invalid namespace given: " + namespace);
			}
			
			dojox.flash.comm.clear(namespace);
		},
		
		remove: function(key, namespace){
			if(!namespace){
				namespace = dojox.storage.DEFAULT_NAMESPACE;		
			}
			
			if(!this.isValidKey(namespace)){
				throw new Error("Invalid namespace given: " + namespace);
			}
			
			dojox.flash.comm.remove(key, namespace);
		},
		
		removeMultiple: function(/*array*/ keys, /*string?*/ namespace){ /*Object*/
			if(!this.isValidKeyArray(keys)){
				dojo.raise("Invalid key array given: " + keys);
			}
			if(!namespace){
				namespace = dojox.storage.DEFAULT_NAMESPACE;		
			}
			
			if(!this.isValidKey(namespace)){
				throw new Error("Invalid namespace given: " + namespace);
			}
			
			var metaKey = keys.join(",");
			dojox.flash.comm.removeMultiple(metaKey, namespace);
		},

		isPermanent: function(){
			return true;
		},

		getMaximumSize: function(){
			return dojox.storage.SIZE_NO_LIMIT;
		},

		hasSettingsUI: function(){
			return true;
		},

		showSettingsUI: function(){
			dojox.flash.comm.showSettings();
			dojox.flash.obj.setVisible(true);
			dojox.flash.obj.center();
		},

		hideSettingsUI: function(){
			// hide the dialog
			dojox.flash.obj.setVisible(false);
			
			// call anyone who wants to know the dialog is
			// now hidden
			if(dojo.isFunction(dojox.storage.onHideSettingsUI)){
				dojox.storage.onHideSettingsUI.call(null);	
			}
		},
		
		getResourceList: function(){ /* Array[] */
			// Dojo Offline no longer uses the FlashStorageProvider for offline
			// storage; Gears is now required
			return [];
		},
		
		/** Called when Flash and the page are finished loading. */
		_loaded: function(){
			// get available namespaces
			this._allNamespaces = this.getNamespaces();
			
			this.initialized = true;

			// indicate that this storage provider is now loaded
			dojox.storage.manager.loaded();
		},
		
		//	Called if the storage system needs to tell us about the status
		//	of a put() request. 
		_onStatus: function(statusResult, key, namespace){
		  //
			var ds = dojox.storage;
			var dfo = dojox.flash.obj;
			
			if(statusResult == ds.PENDING){
				dfo.center();
				dfo.setVisible(true);
			}else{
				dfo.setVisible(false);
			}
			
			if(ds._statusHandler){
				ds._statusHandler.call(null, statusResult, key, null, namespace);		
			}
		}
	}
);

dojox.storage.manager.register("dojox.storage.FlashStorageProvider",
								new dojox.storage.FlashStorageProvider());

}

if(!dojo._hasResource["dojox.storage._common"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.storage._common"] = true;
dojo.provide("dojox.storage._common");



/*
  Note: if you are doing Dojo Offline builds you _must_
  have offlineProfile=true when you run the build script:
  ./build.sh action=release profile=offline offlineProfile=true
*/




// now that we are loaded and registered tell the storage manager to
// initialize itself
dojox.storage.manager.initialize();

}

if(!dojo._hasResource["dojox.storage"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.storage"] = true;
dojo.provide("dojox.storage");


}

if(!dojo._hasResource["dojox.off.files"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.off.files"] = true;
dojo.provide("dojox.off.files");

// Author: Brad Neuberg, bkn3@columbia.edu, http://codinginparadise.org

// summary:
//	Helps maintain resources that should be
//	available offline, such as CSS files.
// description:
//	dojox.off.files makes it easy to indicate
//	what resources should be available offline,
//	such as CSS files, JavaScript, HTML, etc.
dojox.off.files = {
	// versionURL: String
	//	An optional file, that if present, records the version
	//	of our bundle of files to make available offline. If this
	//	file is present, and we are not currently debugging,
	//	then we only refresh our offline files if the version has
	//	changed. 
	versionURL: "version.js",
	
	// listOfURLs: Array
	//	For advanced usage; most developers can ignore this.
	//	Our list of URLs that will be cached and made available
	//	offline.
	listOfURLs: [],
	
	// refreshing: boolean
	//	For advanced usage; most developers can ignore this.
	//	Whether we are currently in the middle
	//	of refreshing our list of offline files.
	refreshing: false,

	_cancelID: null,
	
	_error: false,
	_errorMessages: [],
	_currentFileIndex: 0,
	_store: null,
	_doSlurp: false,
	
	slurp: function(){
		// summary:
		//	Autoscans the page to find all resources to
		//	cache. This includes scripts, images, CSS, and hyperlinks
		//	to pages that are in the same scheme/port/host as this
		//	page. We also scan the embedded CSS of any stylesheets
		//	to find @import statements and url()'s.
		//  You should call this method from the top-level, outside of
		//	any functions and before the page loads:
		//
		//	<script>
		//		
		//		
		//		
		//		
		//
		//		// configure how we should work offline
		//
		//		// set our application name
		//		dojox.off.ui.appName = "Moxie";
		//
		//		// automatically "slurp" the page and
		//		// capture the resources we need offline
		//		dojox.off.files.slurp();
		//
		// 		// tell Dojo Offline we are ready for it to initialize itself now
		//		// that we have finished configuring it for our application
		//		dojox.off.initialize();
		//	</script>
		//
		//	Note that inline styles on elements are not handled (i.e.
		//	if you somehow have an inline style that uses a URL);
		//	object and embed tags are not scanned since their format
		//	differs based on type; and elements created by JavaScript
		//	after page load are not found. For these you must manually
		//	add them with a dojox.off.files.cache() method call.
		
		// just schedule the slurp once the page is loaded and
		// Dojo Offline is ready to slurp; dojox.off will call
		// our _slurp() method before indicating it is finished
		// loading
		this._doSlurp = true;
	},
	
	cache: function(urlOrList){ /* void */
		// summary:
		//		Caches a file or list of files to be available offline. This
		//		can either be a full URL, such as http://foobar.com/index.html,
		//		or a relative URL, such as ../index.html. This URL is not
		//		actually cached until dojox.off.sync.synchronize() is called.
		// urlOrList: String or Array[]
		//		A URL of a file to cache or an Array of Strings of files to
		//		cache
		
		//
		
		if(dojo.isString(urlOrList)){
			var url = this._trimAnchor(urlOrList+"");
			if(!this.isAvailable(url)){ 
				this.listOfURLs.push(url); 
			}
		}else if(urlOrList instanceof dojo._Url){
			var url = this._trimAnchor(urlOrList.uri);
			if(!this.isAvailable(url)){ 
				this.listOfURLs.push(url); 
			}
		}else{
			dojo.forEach(urlOrList, function(url){
				url = this._trimAnchor(url);
				if(!this.isAvailable(url)){ 
					this.listOfURLs.push(url); 
				}
			}, this);
		}
	},
	
	printURLs: function(){
		// summary:
		//	A helper function that will dump and print out
		//	all of the URLs that are cached for offline
		//	availability. This can help with debugging if you
		//	are trying to make sure that all of your URLs are
		//	available offline
		
		dojo.forEach(this.listOfURLs, function(i){
			
		});	
	},
	
	remove: function(url){ /* void */
		// summary:
		//		Removes a URL from the list of files to cache.
		// description:
		//		Removes a URL from the list of URLs to cache. Note that this
		//		does not actually remove the file from the offline cache;
		//		instead, it just prevents us from refreshing this file at a
		//		later time, so that it will naturally time out and be removed
		//		from the offline cache
		// url: String
		//		The URL to remove
		for(var i = 0; i < this.listOfURLs.length; i++){
			if(this.listOfURLs[i] == url){
				this.listOfURLs = this.listOfURLs.splice(i, 1);
				break;
			}
		}
	},
	
	isAvailable: function(url){ /* boolean */
		// summary:
		//		Determines whether the given resource is available offline.
		// url: String
		//	The URL to check
		for(var i = 0; i < this.listOfURLs.length; i++){
			if(this.listOfURLs[i] == url){
				return true;
			}
		}
		
		return false;
	},
	
	refresh: function(callback){ /* void */
		//
		// summary:
		//	For advanced usage; most developers can ignore this.
		//	Refreshes our list of offline resources,
		//	making them available offline.
		// callback: Function
		//	A callback that receives two arguments: whether an error
		//	occurred, which is a boolean; and an array of error message strings
		//	with details on errors encountered. If no error occured then message is
		//	empty array with length 0.
		try{
			if(dojo.config.isDebug){
				this.printURLs();
			}
			
			this.refreshing = true;
			
			if(this.versionURL){
				this._getVersionInfo(function(oldVersion, newVersion, justDebugged){
					//console.warn("getVersionInfo, oldVersion="+oldVersion+", newVersion="+newVersion
					//				+ ", justDebugged="+justDebugged+", isDebug="+dojo.config.isDebug);
					if(dojo.config.isDebug || !newVersion || justDebugged 
							|| !oldVersion || oldVersion != newVersion){
						console.warn("Refreshing offline file list");
						this._doRefresh(callback, newVersion);
					}else{
						console.warn("No need to refresh offline file list");
						callback(false, []);
					}
				});
			}else{
				console.warn("Refreshing offline file list");
				this._doRefresh(callback);
			}
		}catch(e){
			this.refreshing = false;
                       
			// can't refresh files -- core operation --
			// fail fast
			dojox.off.coreOpFailed = true;
			dojox.off.enabled = false;
			dojox.off.onFrameworkEvent("coreOperationFailed");
		}
	},
	
	abortRefresh: function(){
		// summary:
		//	For advanced usage; most developers can ignore this.
		//	Aborts and cancels a refresh.
		if(!this.refreshing){
			return;
		}
		
		this._store.abortCapture(this._cancelID);
		this.refreshing = false;
	},
	
	_slurp: function(){
		if(!this._doSlurp){
			return;
		}
		
		var handleUrl = dojo.hitch(this, function(url){
			if(this._sameLocation(url)){
				this.cache(url);
			}
		});
		
		handleUrl(window.location.href);
		
		dojo.query("script").forEach(function(i){
			try{
				handleUrl(i.getAttribute("src"));
			}catch(exp){
				//
			}
		});
		
		dojo.query("link").forEach(function(i){
			try{
				if(!i.getAttribute("rel")
					|| i.getAttribute("rel").toLowerCase() != "stylesheet"){
					return;
				}
			
				handleUrl(i.getAttribute("href"));
			}catch(exp){
				//
			}
		});
		
		dojo.query("img").forEach(function(i){
			try{
				handleUrl(i.getAttribute("src"));
			}catch(exp){
				//
			}
		});
		
		dojo.query("a").forEach(function(i){
			try{
				handleUrl(i.getAttribute("href"));
			}catch(exp){
				//
			}
		});
		
		// FIXME: handle 'object' and 'embed' tag
		
		// parse our style sheets for inline URLs and imports
		dojo.forEach(document.styleSheets, function(sheet){
			try{
				if(sheet.cssRules){ // Firefox
					dojo.forEach(sheet.cssRules, function(rule){
						var text = rule.cssText;
						if(text){
							var matches = text.match(/url\(\s*([^\) ]*)\s*\)/i);
							if(!matches){
								return;
							}
							
							for(var i = 1; i < matches.length; i++){
								handleUrl(matches[i])
							}
						}
					});
				}else if(sheet.cssText){ // IE
					var matches;
					var text = sheet.cssText.toString();
					// unfortunately, using RegExp.exec seems to be flakey
					// for looping across multiple lines on IE using the
					// global flag, so we have to simulate it
					var lines = text.split(/\f|\r|\n/);
					for(var i = 0; i < lines.length; i++){
						matches = lines[i].match(/url\(\s*([^\) ]*)\s*\)/i);
						if(matches && matches.length){
							handleUrl(matches[1]);
						}
					}
				}
			}catch(exp){
				//
			}
		});
		
		//this.printURLs();
	},
	
	_sameLocation: function(url){
		if(!url){ return false; }
		
		// filter out anchors
		if(url.length && url.charAt(0) == "#"){
			return false;
		}
		
		// FIXME: dojo._Url should be made public;
		// it's functionality is very useful for
		// parsing URLs correctly, which is hard to
		// do right
		url = new dojo._Url(url);
		
		// totally relative -- ../../someFile.html
		if(!url.scheme && !url.port && !url.host){ 
			return true;
		}
		
		// scheme relative with port specified -- brad.com:8080
		if(!url.scheme && url.host && url.port
				&& window.location.hostname == url.host
				&& window.location.port == url.port){
			return true;
		}
		
		// scheme relative with no-port specified -- brad.com
		if(!url.scheme && url.host && !url.port
			&& window.location.hostname == url.host
			&& window.location.port == 80){
			return true;
		}
		
		// else we have everything
		return  window.location.protocol == (url.scheme + ":")
				&& window.location.hostname == url.host
				&& (window.location.port == url.port || !window.location.port && !url.port);
	},
	
	_trimAnchor: function(url){
		return url.replace(/\#.*$/, "");
	},
	
	_doRefresh: function(callback, newVersion){
		// get our local server
		var localServer;
		try{
			localServer = google.gears.factory.create("beta.localserver", "1.0");
		}catch(exp){
			dojo.setObject("google.gears.denied", true);
			dojox.off.onFrameworkEvent("coreOperationFailed");
			throw "Google Gears must be allowed to run";
		}
		
		var storeName = "dot_store_" 
							+ window.location.href.replace(/[^0-9A-Za-z_]/g, "_");
							
		// clip at 64 characters, the max length of a resource store name
		if(storeName.length >= 64){
		  storeName = storeName.substring(0, 63);
		}
			
		// refresh everything by simply removing
		// any older stores
		localServer.removeStore(storeName);
		
		// open/create the resource store
		localServer.openStore(storeName);
		var store = localServer.createStore(storeName);
		this._store = store;

		// add our list of files to capture
		var self = this;
		this._currentFileIndex = 0;
		this._cancelID = store.capture(this.listOfURLs, function(url, success, captureId){
			//
			if(!success && self.refreshing){
				self._cancelID = null;
				self.refreshing = false;
				var errorMsgs = [];
				errorMsgs.push("Unable to capture: " + url);
				callback(true, errorMsgs);
				return;
			}else if(success){
				self._currentFileIndex++;
			}
			
			if(success && self._currentFileIndex >= self.listOfURLs.length){
				self._cancelID = null;
				self.refreshing = false;
				if(newVersion){
					dojox.storage.put("oldVersion", newVersion, null,
									dojox.off.STORAGE_NAMESPACE);
				}
				dojox.storage.put("justDebugged", dojo.config.isDebug, null,
									dojox.off.STORAGE_NAMESPACE);
				callback(false, []);
			}
		});
	},
	
	_getVersionInfo: function(callback){
		var justDebugged = dojox.storage.get("justDebugged", 
									dojox.off.STORAGE_NAMESPACE);
		var oldVersion = dojox.storage.get("oldVersion",
									dojox.off.STORAGE_NAMESPACE);
		var newVersion = null;
		
		callback = dojo.hitch(this, callback);
		
		dojo.xhrGet({
				url: this.versionURL + "?browserbust=" + new Date().getTime(),
				timeout: 5 * 1000,
				handleAs: "javascript",
				error: function(err){
					//console.warn("dojox.off.files._getVersionInfo, err=",err);
					dojox.storage.remove("oldVersion", dojox.off.STORAGE_NAMESPACE);
					dojox.storage.remove("justDebugged", dojox.off.STORAGE_NAMESPACE);
					callback(oldVersion, newVersion, justDebugged);
				},
				load: function(data){
					//console.warn("dojox.off.files._getVersionInfo, load=",data);
					
					// some servers incorrectly return 404's
					// as a real page
					if(data){
						newVersion = data;
					}
					
					callback(oldVersion, newVersion, justDebugged);
				}
		});
	}
}

}

if(!dojo._hasResource["dojox.off.sync"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.off.sync"] = true;
dojo.provide("dojox.off.sync");





// Author: Brad Neuberg, bkn3@columbia.edu, http://codinginparadise.org

// summary:
//		Exposes syncing functionality to offline applications
dojo.mixin(dojox.off.sync, {
	// isSyncing: boolean
	//		Whether we are in the middle of a syncing session.
	isSyncing: false,
	
	// cancelled: boolean
	//		Whether we were cancelled during our last sync request or not. If
	//		we are cancelled, then successful will be false.
	cancelled: false,
	
	// successful: boolean
	//		Whether the last sync was successful or not.  If false, an error
	//		occurred.
	successful: true,
	
	// details: String[]
	//		Details on the sync. If the sync was successful, this will carry
	//		any conflict or merging messages that might be available; if the
	//		sync was unsuccessful, this will have an error message.  For both
	//		of these, this should be an array of Strings, where each string
	//		carries details on the sync. 
	//	Example: 
	//		dojox.off.sync.details = ["The document 'foobar' had conflicts - yours one",
	//						"The document 'hello world' was automatically merged"];
	details: [],
	
	// error: boolean
	//		Whether an error occurred during the syncing process.
	error: false,
	
	// actions: dojox.off.sync.ActionLog
	//		Our ActionLog that we store offline actions into for later
	//		replaying when we go online
	actions: null,
	
	// autoSync: boolean
	//		For advanced usage; most developers can ignore this.
	//		Whether we do automatically sync on page load or when we go online.
	//		If true we do, if false syncing must be manually initiated.
	//		Defaults to true.
	autoSync: true,
	
	// summary:
	//	An event handler that is called during the syncing process with
	//	the state of syncing. It is important that you connect to this
	//	method and respond to certain sync events, especially the 
	//	"download" event.
	// description:
	//	This event handler is called during the syncing process. You can
	//	do a dojo.connect to receive sync feedback:
	//
	//		dojo.connect(dojox.off.sync, "onSync", someFunc);
	//
	//	You will receive one argument, which is the type of the event
	//	and which can have the following values.
	//
	//	The most common two types that you need to care about are "download"
	//	and "finished", especially if you are using the default
	//	Dojo Offline UI widget that does the hard work of informing
	//	the user through the UI about what is occuring during syncing.
	//
	//	If you receive the "download" event, you should make a network call
	//	to retrieve and store your data somehow for offline access. The
	//	"finished" event indicates that syncing is done. An example:
	//	
	//		dojo.connect(dojox.off.sync, "onSync", function(type){
	//			if(type == "download"){
	//				// make a network call to download some data
	//				// for use offline
	//				dojo.xhrGet({
	//					url: 		"downloadData.php",
	//					handleAs:	"javascript",
	//					error:		function(err){
	//						dojox.off.sync.finishedDownloading(false, "Can't download data");
	//					},
	//					load:		function(data){
	//						// store our data
	//						dojox.storage.put("myData", data);
	//
	//						// indicate we are finished downloading
	//						dojox.off.sync.finishedDownloading(true);
	//					}
	//				});
	//			}else if(type == "finished"){
	//				// update UI somehow to indicate we are finished,
	//				// such as using the download data to change the 
	//				// available data
	//			}
	//		})
	//
	//	Here is the full list of event types if you want to do deep
	//	customization, such as updating your UI to display the progress
	//	of syncing (note that the default Dojo Offline UI widget does
	//	this for you if you choose to pull that in). Most of these
	//	are only appropriate for advanced usage and can be safely
	//	ignored:
	//
	//		* "start"
	//				syncing has started
	//		* "refreshFiles"
	//				syncing will begin refreshing
	//				our offline file cache
	//		* "upload"
	//				syncing will begin uploading
	//				any local data changes we have on the client.
	//				This event is fired before we fire
	//				the dojox.off.sync.actions.onReplay event for
	//				each action to replay; use it to completely
	//				over-ride the replaying behavior and prevent
	//				it entirely, perhaps rolling your own sync
	//				protocol if needed.
	//		* "download"
	//				syncing will begin downloading any new data that is
	//				needed into persistent storage. Applications are required to
	//				implement this themselves, storing the required data into
	//				persistent local storage using Dojo Storage.
	//		* "finished"
	//				syncing is finished; this
	//				will be called whether an error ocurred or not; check
	//				dojox.off.sync.successful and dojox.off.sync.error for sync details
	//		* "cancel"
	//				Fired when canceling has been initiated; canceling will be
	//				attempted, followed by the sync event "finished".
	onSync: function(/* String */ type){},
	
	synchronize: function(){ /* void */
		// summary: Starts synchronizing

		//dojo.debug("synchronize");
		if(this.isSyncing || dojox.off.goingOnline || (!dojox.off.isOnline)){
			return;
		}
	
		this.isSyncing = true;
		this.successful = false;
		this.details = [];
		this.cancelled = false;
		
		this.start();
	},
	
	cancel: function(){ /* void */
		// summary:
		//	Attempts to cancel this sync session
		
		if(!this.isSyncing){ return; }
		
		this.cancelled = true;
		if(dojox.off.files.refreshing){
			dojox.off.files.abortRefresh();
		}
		
		this.onSync("cancel");
	},
	
	finishedDownloading: function(successful /* boolean? */, 
									errorMessage /* String? */){
		// summary:
		//		Applications call this method from their
		//		after getting a "download" event in
		//		dojox.off.sync.onSync to signal that
		//		they are finished downloading any data 
		//		that should be available offline
		// successful: boolean?
		//		Whether our downloading was successful or not.
		//		If not present, defaults to true.
		// errorMessage: String?
		//		If unsuccessful, a message explaining why
		if(typeof successful == "undefined"){
			successful = true;
		}
		
		if(!successful){
			this.successful = false;
			this.details.push(errorMessage);
			this.error = true;
		}
		
		this.finished();
	},
	
	start: function(){ /* void */
		// summary:
		//	For advanced usage; most developers can ignore this.
		//	Called at the start of the syncing process. Advanced
		//	developers can over-ride this method to use their
		//	own sync mechanism to start syncing.
		
		if(this.cancelled){
			this.finished();
			return;
		}
		this.onSync("start");
		this.refreshFiles();
	},
	
	refreshFiles: function(){ /* void */
		// summary:
		//	For advanced usage; most developers can ignore this.
		//	Called when we are going to refresh our list
		//	of offline files during syncing. Advanced developers 
		//	can over-ride this method to do some advanced magic related to
		//	refreshing files.
		
		//dojo.debug("refreshFiles");
		if(this.cancelled){
			this.finished();
			return;
		}
		
		this.onSync("refreshFiles");
		
		dojox.off.files.refresh(dojo.hitch(this, function(error, errorMessages){
			if(error){
				this.error = true;
				this.successful = false;
				for(var i = 0; i < errorMessages.length; i++){
					this.details.push(errorMessages[i]);
				}
				
				// even if we get an error while syncing files,
				// keep syncing so we can upload and download
				// data
			}
			
			this.upload();
		}));
	},
	
	upload: function(){ /* void */
		// summary:
		//	For advanced usage; most developers can ignore this.
		//	Called when syncing wants to upload data. Advanced
		//	developers can over-ride this method to completely
		//	throw away the Action Log and replaying system
		//	and roll their own advanced sync mechanism if needed.
		
		if(this.cancelled){
			this.finished();
			return;
		}
		
		this.onSync("upload");
		
		// when we are done uploading start downloading
		dojo.connect(this.actions, "onReplayFinished", this, this.download);
		
		// replay the actions log
		this.actions.replay();
	},
	
	download: function(){ /* void */
		// summary:
		//	For advanced usage; most developers can ignore this.
		//	Called when syncing wants to download data. Advanced
		//	developers can over-ride this method to use their
		//	own sync mechanism.
		
		if(this.cancelled){
			this.finished();
			return;
		}
		
		// apps should respond to the "download"
		// event to download their data; when done
		// they must call dojox.off.sync.finishedDownloading()
		this.onSync("download");
	},
	
	finished: function(){ /* void */
		// summary:
		//	For advanced usage; most developers can ignore this.
		//	Called when syncing is finished. Advanced
		//	developers can over-ride this method to clean
		//	up after finishing their own sync
		//	mechanism they might have rolled.
		this.isSyncing = false;
		
		this.successful = (!this.cancelled && !this.error);
		
		this.onSync("finished");
	},
	
	_save: function(callback){
		this.actions._save(function(){
			callback();
		});
	},
	
	_load: function(callback){
		this.actions._load(function(){
			callback();
		});
	}
});


// summary:
//		A class that records actions taken by a user when they are offline,
//		suitable for replaying when the network reappears. 
// description:
//		The basic idea behind this method is to record user actions that would
//		normally have to contact a server into an action log when we are
//		offline, so that later when we are online we can simply replay this log
//		in the order user actions happened so that they can be executed against
//		the server, causing synchronization to happen. 
//		
//		When we replay, for each of the actions that were added, we call a 
//		method named onReplay that applications should connect to and 
//		which will be called over and over for each of our actions -- 
//		applications should take the offline action
//		information and use it to talk to a server to have this action
//		actually happen online, 'syncing' themselves with the server. 
//
//		For example, if the action was "update" with the item that was updated, we
//		might call some RESTian server API that exists for updating an item in
//		our application.  The server could either then do sophisticated merging
//		and conflict resolution on the server side, for example, allowing you
//		to pop up a custom merge UI, or could do automatic merging or nothing
//		of the sort. When you are finished with this particular action, your
//		application is then required to call continueReplay() on the actionLog object
//		passed to onReplay() to continue replaying the action log, or haltReplay()
//		with the reason for halting to completely stop the syncing/replaying
//		process.
//
//		For example, imagine that we have a web application that allows us to add
//		contacts. If we are offline, and we update a contact, we would add an action;
//		imagine that the user has to click an Update button after changing the values
//		for a given contact:
//	
//		dojox.off.whenOffline(dojo.byId("updateButton"), "onclick", function(evt){
//			// get the updated customer values
//			var customer = getCustomerValues();
//			
//			// we are offline -- just record this action
//			var action = {name: "update", customer: customer};
//			dojox.off.sync.actions.add(action)
//			
//			// persist this customer data into local storage as well
//			dojox.storage.put(customer.name, customer);
//		})
//
//		Then, when we go back online, the dojox.off.sync.actions.onReplay event
//		will fire over and over, once for each action that was recorded while offline:
//
//		dojo.connect(dojox.off.sync.actions, "onReplay", function(action, actionLog){
//			// called once for each action we added while offline, in the order
//			// they were added
//			if(action.name == "update"){
//				var customer = action.customer;
//				
//				// call some network service to update this customer
//				dojo.xhrPost({
//					url: "updateCustomer.php",
//					content: {customer: dojo.toJson(customer)},
//					error: function(err){
//						actionLog.haltReplay(err);
//					},
//					load: function(data){
//						actionLog.continueReplay();
//					}
//				})
//			}
//		})
//
//		Note that the actions log is always automatically persisted locally while using it, so
//		that if the user closes the browser or it crashes the actions will safely be stored
//		for later replaying.
dojo.declare("dojox.off.sync.ActionLog", null, {
		// entries: Array
		//		An array of our action entries, where each one is simply a custom
		//		object literal that were passed to add() when this action entry
		//		was added.
		entries: [],
		
		// reasonHalted: String
		//		If we halted, the reason why
		reasonHalted: null,
		
		// isReplaying: boolean
		//		If true, we are in the middle of replaying a command log; if false,
		//		then we are not
		isReplaying: false,
		
		// autoSave: boolean
		//		Whether we automatically save the action log after each call to
		//		add(); defaults to true. For applications that are rapidly adding
		//		many action log entries in a short period of time, it can be
		//		useful to set this to false and simply call save() yourself when
		//		you are ready to persist your command log -- otherwise performance
		//		could be slow as the default action is to attempt to persist the
		//		actions log constantly with calls to add().
		autoSave: true,
		
		add: function(action /* Object */){ /* void */
			// summary:
			//	Adds an action to our action log
			// description:
			//	This method will add an action to our
			//	action log, later to be replayed when we
			//	go from offline to online. 'action'
			//	will be available when this action is
			//	replayed and will be passed to onReplay.
			//
			//	Example usage:
			//	
			//	dojox.off.sync.log.add({actionName: "create", itemType: "document",
			//					  {title: "Message", content: "Hello World"}});
			// 
			//	The object literal is simply a custom object appropriate
			//	for our application -- it can be anything that preserves the state
			//	of a user action that will be executed when we go back online
			//	and replay this log. In the above example,
			//	"create" is the name of this action; "documents" is the 
			//	type of item this command is operating on, such as documents, contacts,
			//	tasks, etc.; and the final argument is the document that was created. 
			
			if(this.isReplaying){
				throw "Programming error: you can not call "
						+ "dojox.off.sync.actions.add() while "
						+ "we are replaying an action log";
			}
			
			this.entries.push(action);
			
			// save our updated state into persistent
			// storage
			if(this.autoSave){
				this._save();
			}
		},
		
		onReplay: function(action /* Object */, 
							actionLog /* dojox.off.sync.ActionLog */){ /* void */
			// summary:
			//	Called when we replay our log, for each of our action
			//	entries.
			// action: Object
			//	A custom object literal representing an action for this
			//	application, such as 
			//	{actionName: "create", item: {title: "message", content: "hello world"}}
			// actionLog: dojox.off.sync.ActionLog
			//	A reference to the dojox.off.sync.actions log so that developers
			//	can easily call actionLog.continueReplay() or actionLog.haltReplay().
			// description:
			//	This callback should be connected to by applications so that
			//	they can sync themselves when we go back online:
			//
			//		dojo.connect(dojox.off.sync.actions, "onReplay", function(action, actionLog){
			//				// do something
			//		})
			//
			//	When we replay our action log, this callback is called for each
			//	of our action entries in the order they were added. The 
			//	'action' entry that was passed to add() for this action will 
			//	also be passed in to onReplay, so that applications can use this information
			//	to do their syncing, such as contacting a server web-service
			//	to create a new item, for example. 
			// 
			//	Inside the method you connected to onReplay, you should either call
			//	actionLog.haltReplay(reason) if an error occurred and you would like to halt
			//	action replaying or actionLog.continueReplay() to have the action log
			//	continue replaying its log and proceed to the next action; 
			//	the reason you must call these is the action you execute inside of 
			//	onAction will probably be asynchronous, since it will be talking on 
			//	the network, and you should call one of these two methods based on 
			//	the result of your network call.
		},
		
		length: function(){ /* Number */
			// summary:
			//	Returns the length of this 
			//	action log
			return this.entries.length;
		},
		
		haltReplay: function(reason /* String */){ /* void */
			// summary: Halts replaying this command log.
			// reason: String
			//		The reason we halted.
			// description:
			//		This method is called as we are replaying an action log; it
			//		can be called from dojox.off.sync.actions.onReplay, for
			//		example, for an application to indicate an error occurred
			//		while replaying this action, halting further processing of
			//		the action log. Note that any action log entries that
			//		were processed before have their effects retained (i.e.
			//		they are not rolled back), while the action entry that was
			//		halted stays in our list of actions to later be replayed.	
			if(!this.isReplaying){
				return;
			}
			
			if(reason){
				this.reasonHalted = reason.toString();		
			}
			
			// save the state of our action log, then
			// tell anyone who is interested that we are
			// done when we are finished saving
			if(this.autoSave){
				var self = this;
				this._save(function(){
					self.isReplaying = false;
					self.onReplayFinished();
				});
			}else{
				this.isReplaying = false;
				this.onReplayFinished();
			}
		},
		
		continueReplay: function(){ /* void */
			// summary:
			//		Indicates that we should continue processing out list of
			//		actions.
			// description:
			//		This method is called by applications that have overridden
			//		dojox.off.sync.actions.onReplay() to continue replaying our 
			//		action log after the application has finished handling the 
			//		current action.
			if(!this.isReplaying){
				return;
			}
			
			// shift off the old action we just ran
			this.entries.shift();
			
			// are we done?
			if(!this.entries.length){
				// save the state of our action log, then
				// tell anyone who is interested that we are
				// done when we are finished saving
				if(this.autoSave){
					var self = this;
					this._save(function(){
						self.isReplaying = false;
						self.onReplayFinished();
					});
					return;
				}else{
					this.isReplaying = false;
					this.onReplayFinished();
					return;
				}
			}
			
			// get the next action
			var nextAction = this.entries[0];
			this.onReplay(nextAction, this);
		},
		
		clear: function(){ /* void */
			// summary:
			//	Completely clears this action log of its entries
			
			if(this.isReplaying){
				return;
			}
			
			this.entries = [];
			
			// save our updated state into persistent
			// storage
			if(this.autoSave){
				this._save();
			}
		},
		
		replay: function(){ /* void */
			// summary:
			//	For advanced usage; most developers can ignore this.
			//	Replays all of the commands that have been
			//	cached in this command log when we go back online;
			//	onCommand will be called for each command we have
			
			if(this.isReplaying){
				return;
			}
			
			this.reasonHalted = null;
			
			if(!this.entries.length){
				this.onReplayFinished();
				return;
			}
			
			this.isReplaying = true;
			
			var nextAction = this.entries[0];
			this.onReplay(nextAction, this);
		},
		
		// onReplayFinished: Function
		//	For advanced usage; most developers can ignore this.
		//	Called when we are finished replaying our commands;
		//	called if we have successfully exhausted all of our
		//	commands, or if an error occurred during replaying.
		//	The default implementation simply continues the
		//	synchronization process. Connect to this to register
		//	for the event:
		//
		//		dojo.connect(dojox.off.sync.actions, "onReplayFinished", 
		//					someFunc)
		onReplayFinished: function(){
		},

		toString: function(){
			var results = "";
			results += "[";
			
			for(var i = 0; i < this.entries.length; i++){
				results += "{";
				for(var j in this.entries[i]){
					results += j + ": \"" + this.entries[i][j] + "\"";
					results += ", ";
				}
				results += "}, ";
			}
			
			results += "]";
			
			return results;
		},
		
		_save: function(callback){
			if(!callback){
				callback = function(){};
			}
			
			try{
				var self = this;
				var resultsHandler = function(status, key, message){
					//
					if(status == dojox.storage.FAILED){
						dojox.off.onFrameworkEvent("save", 
											{status: dojox.storage.FAILED,
											isCoreSave: true,
											key: key,
											value: message,
											namespace: dojox.off.STORAGE_NAMESPACE});
						callback();
					}else if(status == dojox.storage.SUCCESS){
						callback();
					}
				};
				
				dojox.storage.put("actionlog", this.entries, resultsHandler,
									dojox.off.STORAGE_NAMESPACE);
			}catch(exp){
				
				dojox.off.onFrameworkEvent("save",
							{status: dojox.storage.FAILED,
							isCoreSave: true,
							key: "actionlog",
							value: this.entries,
							namespace: dojox.off.STORAGE_NAMESPACE});
				callback();
			}
		},
		
		_load: function(callback){
			var entries = dojox.storage.get("actionlog", dojox.off.STORAGE_NAMESPACE);
			
			if(!entries){
				entries = [];
			}
			
			this.entries = entries;
			
			callback();
		}
	}
);

dojox.off.sync.actions = new dojox.off.sync.ActionLog();

}

if(!dojo._hasResource["dojox.off._common"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.off._common"] = true;
dojo.provide("dojox.off._common");






// Author: Brad Neuberg, bkn3@columbia.edu, http://codinginparadise.org

// summary:
//		dojox.off is the main object for offline applications.
dojo.mixin(dojox.off, {
	// isOnline: boolean
	//	true if we are online, false if not
	isOnline: false,
	
	// NET_CHECK: int
	//		For advanced usage; most developers can ignore this.
	//		Time in seconds on how often we should check the status of the
	//		network with an automatic background timer. The current default
	//		is 5 seconds.
	NET_CHECK: 5,
	
	// STORAGE_NAMESPACE: String
	//		For advanced usage; most developers can ignore this.
	//		The namespace we use to save core data into Dojo Storage.
	STORAGE_NAMESPACE: "_dot",
	
	// enabled: boolean
	//		For advanced usage; most developers can ignore this.
	//		Whether offline ability is enabled or not. Defaults to true.
	enabled: true,
	
	// availabilityURL: String
	//		For advanced usage; most developers can ignore this.
	//		The URL to check for site availability.  We do a GET request on
	//		this URL to check for site availability.  By default we check for a
	//		simple text file in src/off/network_check.txt that has one value
	//		it, the value '1'.
	availabilityURL: dojo.moduleUrl("dojox", "off/network_check.txt"),
	
	// goingOnline: boolean
	//		For advanced usage; most developers can ignore this.
	//		True if we are attempting to go online, false otherwise
	goingOnline: false,
	
	// coreOpFailed: boolean
	//		For advanced usage; most developers can ignore this.
	//		A flag set by the Dojo Offline framework that indicates that the
	//		user denied some operation that required the offline cache or an
	//		operation failed in some critical way that was unrecoverable. For
	//		example, if the offline cache is Google Gears and we try to get a
	//		Gears database, a popup window appears asking the user whether they
	//		will approve or deny this request. If the user denies the request,
	//		and we are doing some operation that is core to Dojo Offline, then
	//		we set this flag to 'true'.  This flag causes a 'fail fast'
	//		condition, turning off offline ability.
	coreOpFailed: false,
	
	// doNetChecking: boolean
	//		For advanced usage; most developers can ignore this.
	//		Whether to have a timing interval in the background doing automatic
	//		network checks at regular intervals; the length of time between
	//		checks is controlled by dojox.off.NET_CHECK. Defaults to true.
	doNetChecking: true,
	
	// hasOfflineCache: boolean
	//		For advanced usage; most developers can ignore this.
	//  	Determines if an offline cache is available or installed; an
	//  	offline cache is a facility that can truely cache offline
	//  	resources, such as JavaScript, HTML, etc. in such a way that they
	//  	won't be removed from the cache inappropriately like a browser
	//  	cache would. If this is false then an offline cache will be
	//  	installed. Only Google Gears is currently supported as an offline
	//  	cache. Future possible offline caches include Firefox 3.
	hasOfflineCache: null,
	
	// browserRestart: boolean
	//		For advanced usage; most developers can ignore this.
	//		If true, the browser must be restarted to register the existence of
	//		a new host added offline (from a call to addHostOffline); if false,
	//		then nothing is needed.
	browserRestart: false,
	
	_STORAGE_APP_NAME: window.location.href.replace(/[^0-9A-Za-z_]/g, "_"),
	
	_initializeCalled: false,
	_storageLoaded: false,
	_pageLoaded: false,
	
	onLoad: function(){
		// summary:
		//	Called when Dojo Offline can be used.
		// description:
		//	Do a dojo.connect to this to know when you can
		//	start using Dojo Offline:
		//		dojo.connect(dojox.off, "onLoad", myFunc);
	},
	
	onNetwork: function(type){
		// summary:
		//	Called when our on- or offline- status changes.
		// description:
		//	If we move online, then this method is called with the
		//	value "online". If we move offline, then this method is
		//	called with the value "offline". You can connect to this
		//	method to do add your own behavior:
		//
		//		dojo.connect(dojox.off, "onNetwork", someFunc)
		//
		//	Note that if you are using the default Dojo Offline UI
		//	widget that most of the on- and off-line notification
		//	and syncing is automatically handled and provided to the
		//	user.
		// type: String
		//	Either "online" or "offline".
	},
	
	initialize: function(){ /* void */
		// summary:
		//		Called when a Dojo Offline-enabled application is finished
		//		configuring Dojo Offline, and is ready for Dojo Offline to
		//		initialize itself.
		// description:
		//		When an application has finished filling out the variables Dojo
		//		Offline needs to work, such as dojox.off.ui.appName, it must
		//		this method to tell Dojo Offline to initialize itself.
		
		//		Note:
		//		This method is needed for a rare edge case. In some conditions,
		//		especially if we are dealing with a compressed Dojo build, the
		//		entire Dojo Offline subsystem might initialize itself and be
		//		running even before the JavaScript for an application has had a
		//		chance to run and configure Dojo Offline, causing Dojo Offline
		//		to have incorrect initialization parameters for a given app,
		//		such as no value for dojox.off.ui.appName. This method is
		//		provided to prevent this scenario, to slightly 'slow down' Dojo
		//		Offline so it can be configured before running off and doing
		//		its thing.	

		//
		this._initializeCalled = true;
		
		if(this._storageLoaded && this._pageLoaded){
			this._onLoad();
		}
	},
	
	goOffline: function(){ /* void */
		// summary:
		//		For advanced usage; most developers can ignore this.
		//		Manually goes offline, away from the network.
		if((dojox.off.sync.isSyncing)||(this.goingOnline)){ return; }
		
		this.goingOnline = false;
		this.isOnline = false;
	},
	
	goOnline: function(callback){ /* void */
		// summary: 
		//		For advanced usage; most developers can ignore this.
		//		Attempts to go online.
		// description:
		//		Attempts to go online, making sure this web application's web
		//		site is available. 'callback' is called asychronously with the
		//		result of whether we were able to go online or not.
		// callback: Function
		//		An optional callback function that will receive one argument:
		//		whether the site is available or not and is boolean. If this
		//		function is not present we call dojo.xoff.onOnline instead if
		//		we are able to go online.
		
		//
		
		if(dojox.off.sync.isSyncing || dojox.off.goingOnline){
			return;
		}
		
		this.goingOnline = true;
		this.isOnline = false;
		
		// see if can reach our web application's web site
		this._isSiteAvailable(callback);
	},
	
	onFrameworkEvent: function(type /* String */, saveData /* Object? */){
		//	summary:
		//		For advanced usage; most developers can ignore this.
		//		A standard event handler that can be attached to to find out
		//		about low-level framework events. Most developers will not need to
		//		attach to this method; it is meant for low-level information
		//		that can be useful for updating offline user-interfaces in
		//		exceptional circumstances. The default Dojo Offline UI
		//		widget takes care of most of these situations.
		//	type: String
		//		The type of the event:
		//
		//		* "offlineCacheInstalled"
		//			An event that is fired when a user
		//			has installed an offline cache after the page has been loaded.
		//			If a user didn't have an offline cache when the page loaded, a
		//			UI of some kind might have prompted them to download one. This
		//			method is called if they have downloaded and installed an
		//			offline cache so a UI can reinitialize itself to begin using
		//			this offline cache.
		//		* "coreOperationFailed"
		//			Fired when a core operation during interaction with the
		//			offline cache is denied by the user. Some offline caches, such
		//			as Google Gears, prompts the user to approve or deny caching
		//			files, using the database, and more. If the user denies a
		//			request that is core to Dojo Offline's operation, we set
		//			dojox.off.coreOpFailed to true and call this method for
		//			listeners that would like to respond some how to Dojo Offline
		//			'failing fast'.
		//		* "save"
		//			Called whenever the framework saves data into persistent
		//			storage. This could be useful for providing save feedback
		//			or providing appropriate error feedback if saving fails 
		//			due to a user not allowing the save to occur
		//	saveData: Object?
		//		If the type was 'save', then a saveData object is provided with
		//		further save information. This object has the following properties:	
		//
		//		* status - dojox.storage.SUCCESS, dojox.storage.PENDING, dojox.storage.FAILED
		//		Whether the save succeeded, whether it is pending based on a UI
		//		dialog asking the user for permission, or whether it failed. 	
		//
		//		* isCoreSave - boolean
		//		If true, then this save was for a core piece of data necessary
		//		for the functioning of Dojo Offline. If false, then it is a
		//		piece of normal data being saved for offline access. Dojo
		//		Offline will 'fail fast' if some core piece of data could not
		//		be saved, automatically setting dojox.off.coreOpFailed to
		//		'true' and dojox.off.enabled to 'false'.
		//
		// 		* key - String
		//		The key that we are attempting to persist
		//
		// 		* value - Object
		//		The object we are trying to persist
		//
		// 		* namespace - String
		//		The Dojo Storage namespace we are saving this key/value pair
		//		into, such as "default", "Documents", "Contacts", etc.
		//		Optional.
		if(type == "save"){
			if(saveData.isCoreSave && (saveData.status == dojox.storage.FAILED)){
				dojox.off.coreOpFailed = true;
				dojox.off.enabled = false;
			
				// FIXME: Stop the background network thread
				dojox.off.onFrameworkEvent("coreOperationFailed");
			}
		}else if(type == "coreOperationFailed"){
			dojox.off.coreOpFailed = true;
			dojox.off.enabled = false;
			// FIXME: Stop the background network thread
		}
	},
	
	_checkOfflineCacheAvailable: function(callback){
		// is a true, offline cache running on this machine?
		this.hasOfflineCache = dojo.gears.available;
		
		callback();
	},
	
	_onLoad: function(){
		//
		
		// both local storage and the page are finished loading
		
		// cache the Dojo JavaScript -- just use the default dojo.js
		// name for the most common scenario
		// FIXME: TEST: Make sure syncing doesn't break if dojo.js
		// can't be found, or report an error to developer
		dojox.off.files.cache(dojo.moduleUrl("dojo", "dojo.js"));
		
		// pull in the files needed by Dojo
		this._cacheDojoResources();
		
		// FIXME: need to pull in the firebug lite files here!
		// workaround or else we will get an error on page load
		// from Dojo that it can't find 'console.debug' for optimized builds
		// dojox.off.files.cache(dojo.config.baseRelativePath + "src/debug.js");
		
		// make sure that resources needed by all of our underlying
		// Dojo Storage storage providers will be available
		// offline
		dojox.off.files.cache(dojox.storage.manager.getResourceList());
		
		// slurp the page if the end-developer wants that
		dojox.off.files._slurp();
		
		// see if we have an offline cache; when done, move
		// on to the rest of our startup tasks
		this._checkOfflineCacheAvailable(dojo.hitch(this, "_onOfflineCacheChecked"));
	},
	
	_onOfflineCacheChecked: function(){
		// this method is part of our _onLoad series of startup tasks
		
		// if we have an offline cache, see if we have been added to the 
		// list of available offline web apps yet
		if(this.hasOfflineCache && this.enabled){
			// load framework data; when we are finished, continue
			// initializing ourselves
			this._load(dojo.hitch(this, "_finishStartingUp"));
		}else if(this.hasOfflineCache && !this.enabled){
			// we have an offline cache, but it is disabled for some reason
			// perhaps due to the user denying a core operation
			this._finishStartingUp();
		}else{
			this._keepCheckingUntilInstalled();
		}
	},
	
	_keepCheckingUntilInstalled: function(){
		// this method is part of our _onLoad series of startup tasks
		
		// kick off a background interval that keeps
		// checking to see if an offline cache has been
		// installed since this page loaded
			
		// FIXME: Gears: See if we are installed somehow after the
		// page has been loaded
		
		// now continue starting up
		this._finishStartingUp();
	},
	
	_finishStartingUp: function(){
		//
		
		// this method is part of our _onLoad series of startup tasks
		
		if(!this.hasOfflineCache){
			this.onLoad();
		}else if(this.enabled){
			// kick off a thread to check network status on
			// a regular basis
			this._startNetworkThread();

			// try to go online
			this.goOnline(dojo.hitch(this, function(){
				//
				// indicate we are ready to be used
				dojox.off.onLoad();
			}));
		}else{ // we are disabled or a core operation failed
			if(this.coreOpFailed){
				this.onFrameworkEvent("coreOperationFailed");
			}else{
				this.onLoad();
			}
		}
	},
	
	_onPageLoad: function(){
		//
		this._pageLoaded = true;
		
		if(this._storageLoaded && this._initializeCalled){
			this._onLoad();
		}
	},
	
	_onStorageLoad: function(){
		//
		this._storageLoaded = true;
		
		// were we able to initialize storage? if
		// not, then this is a core operation, and
		// let's indicate we will need to fail fast
		if(!dojox.storage.manager.isAvailable()
			&& dojox.storage.manager.isInitialized()){
			this.coreOpFailed = true;
			this.enabled = false;
		}
		
		if(this._pageLoaded && this._initializeCalled){
			this._onLoad();		
		}
	},
	
	_isSiteAvailable: function(callback){
		// summary:
		//		Determines if our web application's website is available.
		// description:
		//		This method will asychronously determine if our web
		//		application's web site is available, which is a good proxy for
		//		network availability. The URL dojox.off.availabilityURL is
		//		used, which defaults to this site's domain name (ex:
		//		foobar.com). We check for dojox.off.AVAILABILITY_TIMEOUT (in
		//		seconds) and abort after that
		// callback: Function
		//		An optional callback function that will receive one argument:
		//		whether the site is available or not and is boolean. If this
		//		function is not present we call dojox.off.onNetwork instead if we
		//		are able to go online.
		dojo.xhrGet({
			url:		this._getAvailabilityURL(),
			handleAs:	"text",
			timeout:	this.NET_CHECK * 1000, 
			error:		dojo.hitch(this, function(err){
				//
				this.goingOnline = false;
				this.isOnline = false;
				if(callback){ callback(false); }
			}),
			load:		dojo.hitch(this, function(data){
				//
				this.goingOnline = false;
				this.isOnline = true;
				
				if(callback){ callback(true);
				}else{ this.onNetwork("online"); }
			})
		});
	},
	
	_startNetworkThread: function(){
		//
		
		// kick off a thread that does periodic
		// checks on the status of the network
		if(!this.doNetChecking){
			return;
		}
		
		window.setInterval(dojo.hitch(this, function(){	
			var d = dojo.xhrGet({
				url:	 	this._getAvailabilityURL(),
				handleAs:	"text",
				timeout: 	this.NET_CHECK * 1000,
				error:		dojo.hitch(this, 
								function(err){
									if(this.isOnline){
										this.isOnline = false;
										
										// FIXME: xhrGet() is not
										// correctly calling abort
										// on the XHR object when
										// it times out; fix inside
										// there instead of externally
										// here
										try{
											if(typeof d.ioArgs.xhr.abort == "function"){
												d.ioArgs.xhr.abort();
											}
										}catch(e){}
					
										// if things fell in the middle of syncing, 
										// stop syncing
										dojox.off.sync.isSyncing = false;
					
										this.onNetwork("offline");
									}
								}
							),
				load:		dojo.hitch(this, 
								function(data){
									if(!this.isOnline){
										this.isOnline = true;
										this.onNetwork("online");
									}
								}
							)
			});

		}), this.NET_CHECK * 1000);
	},
	
	_getAvailabilityURL: function(){
		var url = this.availabilityURL.toString();
		
		// bust the browser's cache to make sure we are really talking to
		// the server
		if(url.indexOf("?") == -1){
			url += "?";
		}else{
			url += "&";
		}
		url += "browserbust=" + new Date().getTime();
		
		return url;
	},
	
	_onOfflineCacheInstalled: function(){
		this.onFrameworkEvent("offlineCacheInstalled");
	},
	
	_cacheDojoResources: function(){
		// if we are a non-optimized build, then the core Dojo bootstrap
		// system was loaded as separate JavaScript files;
		// add these to our offline cache list. these are
		// loaded before the dojo.require() system exists
		
		// FIXME: create a better mechanism in the Dojo core to
		// expose whether you are dealing with an optimized build;
		// right now we just scan the SCRIPT tags attached to this
		// page and see if there is one for _base/_loader/bootstrap.js
		var isOptimizedBuild = true;
		dojo.forEach(dojo.query("script"), function(i){
			var src = i.getAttribute("src");
			if(!src){ return; }
			
			if(src.indexOf("_base/_loader/bootstrap.js") != -1){
				isOptimizedBuild = false;
			}
		});
		
		if(!isOptimizedBuild){
			dojox.off.files.cache(dojo.moduleUrl("dojo", "_base.js").uri);
			dojox.off.files.cache(dojo.moduleUrl("dojo", "_base/_loader/loader.js").uri);
			dojox.off.files.cache(dojo.moduleUrl("dojo", "_base/_loader/bootstrap.js").uri);
			
			// FIXME: pull in the host environment file in a more generic way
			// for other host environments
			dojox.off.files.cache(dojo.moduleUrl("dojo", "_base/_loader/hostenv_browser.js").uri);
		}
		
		// add anything that was brought in with a 
		// dojo.require() that resulted in a JavaScript
		// URL being fetched
		
		// FIXME: modify dojo/_base/_loader/loader.js to
		// expose a public API to get this information
	
		for(var i = 0; i < dojo._loadedUrls.length; i++){
			dojox.off.files.cache(dojo._loadedUrls[i]);
		}
		
		// FIXME: add the standard Dojo CSS file
	},
	
	_save: function(){
		// summary:
		//		Causes the Dojo Offline framework to save its configuration
		//		data into local storage.	
	},
	
	_load: function(callback){
		// summary:
		//		Causes the Dojo Offline framework to load its configuration
		//		data from local storage
		dojox.off.sync._load(callback);
	}
});


// wait until the storage system is finished loading
dojox.storage.manager.addOnLoad(dojo.hitch(dojox.off, "_onStorageLoad"));

// wait until the page is finished loading
dojo.addOnLoad(dojox.off, "_onPageLoad");

}

if(!dojo._hasResource["dojox.off"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.off"] = true;
dojo.provide("dojox.off");


}

if(!dojo._hasResource["dojox.off.ui"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.off.ui"] = true;
dojo.provide("dojox.off.ui");





// Author: Brad Neuberg, bkn3@columbia.edu, http://codinginparadise.org

// summary:
//	dojox.off.ui provides a standard,
//	default user-interface for a 
//	Dojo Offline Widget that can easily
//	be dropped into applications that would
//	like to work offline.
dojo.mixin(dojox.off.ui, {
	// appName: String
	//	This application's name, such as "Foobar". Note that
	//	this is a string, not HTML, so embedded markup will
	//	not work, including entities. Only the following
	//	characters are allowed: numbers, letters, and spaces.
	//	You must set this property.
	appName: "setme",
	
	// autoEmbed: boolean
	//	For advanced usage; most developers can ignore this.
	//	Whether to automatically auto-embed the default Dojo Offline
	//	widget into this page; default is true. 
	autoEmbed: true,
	
	// autoEmbedID: String
	//	For advanced usage; most developers can ignore this.
	//	The ID of the DOM element that will contain our
	//	Dojo Offline widget; defaults to the ID 'dot-widget'.
	autoEmbedID: "dot-widget",
	
	// runLink: String
	//	For advanced usage; most developers can ignore this.
	//	The URL that should be navigated to to run this 
	//	application offline; this will be placed inside of a
	//	link that the user can drag to their desktop and double
	//	click. Note that this URL must exactly match the URL
	//	of the main page of our resource that is offline for
	//	it to be retrieved from the offline cache correctly.
	//	For example, if you have cached your main page as
	//	http://foobar.com/index.html, and you set this to
	//	http://www.foobar.com/index.html, the run link will
	//	not work. By default this value is automatically set to 
	//	the URL of this page, so it does not need to be set
	//	manually unless you have unusual needs.
	runLink: window.location.href,
	
	// runLinkTitle: String
	//	For advanced usage; most developers can ignore this.
	//	The text that will be inside of the link that a user
	//	can drag to their desktop to run this application offline.
	//	By default this is automatically set to "Run " plus your
	//	application's name.
	runLinkTitle: "Run Application",
	
	// learnHowPath: String
	//	For advanced usage; most developers can ignore this.
	//	The path to a web page that has information on 
	//	how to use this web app offline; defaults to
	//	src/off/ui-template/learnhow.html, relative to
	//	your Dojo installation. Make sure to set
	//	dojo.to.ui.customLearnHowPath to true if you want
	//	a custom Learn How page.
	learnHowPath: dojo.moduleUrl("dojox", "off/resources/learnhow.html"),
	
	// customLearnHowPath: boolean
	//	For advanced usage; most developers can ignore this.
	//	Whether the developer is using their own custom page
	//	for the Learn How instructional page; defaults to false.
	//	Use in conjunction with dojox.off.ui.learnHowPath.
	customLearnHowPath: false,
	
	htmlTemplatePath: dojo.moduleUrl("dojox", "off/resources/offline-widget.html").uri,
	cssTemplatePath: dojo.moduleUrl("dojox", "off/resources/offline-widget.css").uri,
	onlineImagePath: dojo.moduleUrl("dojox", "off/resources/greenball.png").uri,
	offlineImagePath: dojo.moduleUrl("dojox", "off/resources/redball.png").uri,
	rollerImagePath: dojo.moduleUrl("dojox", "off/resources/roller.gif").uri,
	checkmarkImagePath: dojo.moduleUrl("dojox", "off/resources/checkmark.png").uri,
	learnHowJSPath: dojo.moduleUrl("dojox", "off/resources/learnhow.js").uri,
	
	_initialized: false,
	
	onLoad: function(){
		// summary:
		//	A function that should be connected to allow your
		//	application to know when Dojo Offline, the page, and
		//	the Offline Widget are all initialized and ready to be
		//	used:
		//
		//		dojo.connect(dojox.off.ui, "onLoad", someFunc)
	},

	_initialize: function(){
		//
		
		// make sure our app name is correct
		if(this._validateAppName(this.appName) == false){
			alert("You must set dojox.off.ui.appName; it can only contain "
					+ "letters, numbers, and spaces; right now it "
					+ "is incorrectly set to '" + dojox.off.ui.appName + "'");
			dojox.off.enabled = false;
			return;
		}
		
		// set our run link text to its default
		this.runLinkText = "Run " + this.appName;
		
		// setup our event listeners for Dojo Offline events
		// to update our UI
		dojo.connect(dojox.off, "onNetwork", this, "_onNetwork");
		dojo.connect(dojox.off.sync, "onSync", this, "_onSync");
		
		// cache our default UI resources
		dojox.off.files.cache([
							this.htmlTemplatePath,
							this.cssTemplatePath,
							this.onlineImagePath,
							this.offlineImagePath,
							this.rollerImagePath,
							this.checkmarkImagePath
							]);
		
		// embed the offline widget UI
		if(this.autoEmbed){
			this._doAutoEmbed();
		}
	},
	
	_doAutoEmbed: function(){
		// fetch our HTML for the offline widget

		// dispatch the request
		dojo.xhrGet({
			url:	 this.htmlTemplatePath,
			handleAs:	"text",
			error:		function(err){
				dojox.off.enabled = false;
				err = err.message||err;
				alert("Error loading the Dojo Offline Widget from "
						+ this.htmlTemplatePath + ": " + err);
			},
			load:		dojo.hitch(this, this._templateLoaded)	 
		});
	},
	
	_templateLoaded: function(data){
		//
		// inline our HTML
		var container = dojo.byId(this.autoEmbedID);
		if(container){ container.innerHTML = data; }
		
		// fill out our image paths
		this._initImages();
		
		// update our network indicator status ball
		this._updateNetIndicator();
		
		// update our 'Learn How' text
		this._initLearnHow();
		
		this._initialized = true;
		
		// check offline cache settings
		if(!dojox.off.hasOfflineCache){
			this._showNeedsOfflineCache();
			return;
		}
		
		// check to see if we need a browser restart
		// to be able to use this web app offline
		if(dojox.off.hasOfflineCache && dojox.off.browserRestart){
			this._needsBrowserRestart();
			return;
		}else{
			var browserRestart = dojo.byId("dot-widget-browser-restart");
			if(browserRestart){ browserRestart.style.display = "none"; }
		}
		
		// update our sync UI
		this._updateSyncUI();
		
		// register our event listeners for our main buttons
		this._initMainEvtHandlers();
		
		// if offline functionality is disabled, disable everything
		this._setOfflineEnabled(dojox.off.enabled);
		
		// update our UI based on the state of the network
		this._onNetwork(dojox.off.isOnline ? "online" : "offline");
		
		// try to go online
		this._testNet();
	},
	
	_testNet: function(){
		dojox.off.goOnline(dojo.hitch(this, function(isOnline){
			//
			
			// display our online/offline results
			this._onNetwork(isOnline ? "online" : "offline");
			
			// indicate that our default UI 
			// and Dojo Offline are now ready to
			// be used
			this.onLoad();
		}));
	},
	
	_updateNetIndicator: function(){
		var onlineImg = dojo.byId("dot-widget-network-indicator-online");
		var offlineImg = dojo.byId("dot-widget-network-indicator-offline");
		var titleText = dojo.byId("dot-widget-title-text");
		
		if(onlineImg && offlineImg){
			if(dojox.off.isOnline == true){
				onlineImg.style.display = "inline";
				offlineImg.style.display = "none";
			}else{
				onlineImg.style.display = "none";
				offlineImg.style.display = "inline";
			}
		}
		
		if(titleText){
			if(dojox.off.isOnline){
				titleText.innerHTML = "Online";
			}else{
				titleText.innerHTML = "Offline";
			}
		}
	},
	
	_initLearnHow: function(){
		var learnHow = dojo.byId("dot-widget-learn-how-link");
		
		if(!learnHow){ return; }
		
		if(!this.customLearnHowPath){
			// add parameters to URL so the Learn How page
			// can customize itself and display itself
			// correctly based on framework settings
			var dojoPath = dojo.config.baseRelativePath;
			this.learnHowPath += "?appName=" + encodeURIComponent(this.appName)
									+ "&hasOfflineCache=" + dojox.off.hasOfflineCache
									+ "&runLink=" + encodeURIComponent(this.runLink)
									+ "&runLinkText=" + encodeURIComponent(this.runLinkText)
									+ "&baseRelativePath=" + encodeURIComponent(dojoPath);
			
			// cache our Learn How JavaScript page and
			// the HTML version with full query parameters
			// so it is available offline without a cache miss					
			dojox.off.files.cache(this.learnHowJSPath);
			dojox.off.files.cache(this.learnHowPath);
		}
		
		learnHow.setAttribute("href", this.learnHowPath);
		
		var appName = dojo.byId("dot-widget-learn-how-app-name");
		
		if(!appName){ return; }
		
		appName.innerHTML = "";
		appName.appendChild(document.createTextNode(this.appName));
	},
	
	_validateAppName: function(appName){
		if(!appName){ return false; }
		
		return (/^[a-z0-9 ]*$/i.test(appName));
	},
	
	_updateSyncUI: function(){
		var roller = dojo.byId("dot-roller");
		var checkmark = dojo.byId("dot-success-checkmark");
		var syncMessages = dojo.byId("dot-sync-messages");
		var details = dojo.byId("dot-sync-details");
		var cancel = dojo.byId("dot-sync-cancel");
		
		if(dojox.off.sync.isSyncing){
			this._clearSyncMessage();
			
			if(roller){ roller.style.display = "inline"; }
			
			if(checkmark){ checkmark.style.display = "none"; }
			
			if(syncMessages){
				dojo.removeClass(syncMessages, "dot-sync-error");
			}
			
			if(details){ details.style.display = "none"; }
			
			if(cancel){ cancel.style.display = "inline"; }
		}else{	
			if(roller){ roller.style.display = "none"; }
			
			if(cancel){ cancel.style.display = "none"; }
			
			if(syncMessages){
				dojo.removeClass(syncMessages, "dot-sync-error");
			}
		}
	},
	
	_setSyncMessage: function(message){
		var syncMessage = dojo.byId("dot-sync-messages");
		if(syncMessage){
			// when used with Google Gears pre-release in Firefox/Mac OS X,
			// the browser would crash when testing in Moxie
			// if we set the message this way for some reason.
			// Brad Neuberg, bkn3@columbia.edu
			//syncMessage.innerHTML = message;
			
			while(syncMessage.firstChild){
				syncMessage.removeChild(syncMessage.firstChild);
			}
			syncMessage.appendChild(document.createTextNode(message));
		}
	},
	
	_clearSyncMessage: function(){
		this._setSyncMessage("");
	},
	
	_initImages: function(){	
		var onlineImg = dojo.byId("dot-widget-network-indicator-online");
		if(onlineImg){
			onlineImg.setAttribute("src", this.onlineImagePath);
		}
		
		var offlineImg = dojo.byId("dot-widget-network-indicator-offline");
		if(offlineImg){
			offlineImg.setAttribute("src", this.offlineImagePath);
		}
		
		var roller = dojo.byId("dot-roller");
		if(roller){
			roller.setAttribute("src", this.rollerImagePath);
		}
		
		var checkmark = dojo.byId("dot-success-checkmark");
		if(checkmark){
			checkmark.setAttribute("src", this.checkmarkImagePath);
		}
	},
	
	_showDetails: function(evt){
		// cancel the button's default behavior
		evt.preventDefault();
		evt.stopPropagation();
		
		if(!dojox.off.sync.details.length){
			return;
		}
		
		// determine our HTML message to display
		var html = "";
		html += "<html><head><title>Sync Details</title><head><body>";
		html += "<h1>Sync Details</h1>\n";
		html += "<ul>\n";
		for(var i = 0; i < dojox.off.sync.details.length; i++){
			html += "<li>";
			html += dojox.off.sync.details[i];
			html += "</li>";	
		}
		html += "</ul>\n";
		html += "<a href='javascript:window.close()' "
				 + "style='text-align: right; padding-right: 2em;'>"
				 + "Close Window"
				 + "</a>\n";
		html += "</body></html>";
		
		// open a popup window with this message
		var windowParams = "height=400,width=600,resizable=true,"
							+ "scrollbars=true,toolbar=no,menubar=no,"
							+ "location=no,directories=no,dependent=yes";

		var popup = window.open("", "SyncDetails", windowParams);
		
		if(!popup){ // aggressive popup blocker
			alert("Please allow popup windows for this domain; can't display sync details window");
			return;
		}
		
		popup.document.open();
		popup.document.write(html);
		popup.document.close();
		
		// put the focus on the popup window
		if(popup.focus){
			popup.focus();
		}
	},
	
	_cancel: function(evt){
		// cancel the button's default behavior
		evt.preventDefault();
		evt.stopPropagation();
		
		dojox.off.sync.cancel();
	},
	
	_needsBrowserRestart: function(){
		var browserRestart = dojo.byId("dot-widget-browser-restart");
		if(browserRestart){
			dojo.addClass(browserRestart, "dot-needs-browser-restart");
		}
		
		var appName = dojo.byId("dot-widget-browser-restart-app-name");
		if(appName){
			appName.innerHTML = "";
			appName.appendChild(document.createTextNode(this.appName));
		}
		
		var status = dojo.byId("dot-sync-status");
		if(status){
			status.style.display = "none";
		}
	},
	
	_showNeedsOfflineCache: function(){
		var widgetContainer = dojo.byId("dot-widget-container");
		if(widgetContainer){
			dojo.addClass(widgetContainer, "dot-needs-offline-cache");
		}
	},
	
	_hideNeedsOfflineCache: function(){
		var widgetContainer = dojo.byId("dot-widget-container");
		if(widgetContainer){
			dojo.removeClass(widgetContainer, "dot-needs-offline-cache");
		}
	},
	
	_initMainEvtHandlers: function(){
		var detailsButton = dojo.byId("dot-sync-details-button");
		if(detailsButton){
			dojo.connect(detailsButton, "onclick", this, this._showDetails);
		}
		var cancelButton = dojo.byId("dot-sync-cancel-button");
		if(cancelButton){
			dojo.connect(cancelButton, "onclick", this, this._cancel);
		}
	},
	
	_setOfflineEnabled: function(enabled){
		var elems = [];
		elems.push(dojo.byId("dot-sync-status"));
		
		for(var i = 0; i < elems.length; i++){
			if(elems[i]){
				elems[i].style.visibility = 
							(enabled ? "visible" : "hidden");
			}
		}
	},
	
	_syncFinished: function(){
		this._updateSyncUI();
		
		var checkmark = dojo.byId("dot-success-checkmark");
		var details = dojo.byId("dot-sync-details");
		
		if(dojox.off.sync.successful == true){
			this._setSyncMessage("Sync Successful");
			if(checkmark){ checkmark.style.display = "inline"; }
		}else if(dojox.off.sync.cancelled == true){
			this._setSyncMessage("Sync Cancelled");
			
			if(checkmark){ checkmark.style.display = "none"; }
		}else{
			this._setSyncMessage("Sync Error");
			
			var messages = dojo.byId("dot-sync-messages");
			if(messages){
				dojo.addClass(messages, "dot-sync-error");
			}
			
			if(checkmark){ checkmark.style.display = "none"; }
		}
		
		if(dojox.off.sync.details.length && details){
			details.style.display = "inline";
		}
	},
	
	_onFrameworkEvent: function(type, saveData){
		if(type == "save"){
			if(saveData.status == dojox.storage.FAILED && !saveData.isCoreSave){
				alert("Please increase the amount of local storage available "
						+ "to this application");
				if(dojox.storage.hasSettingsUI()){
					dojox.storage.showSettingsUI();
				}		
			
				// FIXME: Be able to know if storage size has changed
				// due to user configuration
			}
		}else if(type == "coreOperationFailed"){
			
		
			if(!this._userInformed){
				alert("This application will not work if Google Gears is not allowed to run");
				this._userInformed = true;
			}
		}else if(type == "offlineCacheInstalled"){
			// clear out the 'needs offline cache' info
			this._hideNeedsOfflineCache();
		
			// check to see if we need a browser restart
			// to be able to use this web app offline
			if(dojox.off.hasOfflineCache == true
				&& dojox.off.browserRestart == true){
				this._needsBrowserRestart();
				return;
			}else{
				var browserRestart = dojo.byId("dot-widget-browser-restart");
				if(browserRestart){
					browserRestart.style.display = "none";
				}
			}
		
			// update our sync UI
			this._updateSyncUI();
		
			// register our event listeners for our main buttons
			this._initMainEvtHandlers();
		
			// if offline is disabled, disable everything
			this._setOfflineEnabled(dojox.off.enabled);
		
			// try to go online
			this._testNet();
		}
	},
	
	_onSync: function(type){
		//
		switch(type){
			case "start": 
				this._updateSyncUI();
				break;
				
			case "refreshFiles":
				this._setSyncMessage("Downloading UI...");
				break;
				
			case "upload":
				this._setSyncMessage("Uploading new data...");
				break;
				
			case "download":
				this._setSyncMessage("Downloading new data...");
				break;
				
			case "finished":
				this._syncFinished();
				break;
				
			case "cancel":
				this._setSyncMessage("Canceling Sync...");
				break;
				
			default:
				dojo.warn("Programming error: "
							+ "Unknown sync type in dojox.off.ui: " + type);
				break;
		}
	},
	
	_onNetwork: function(type){
		// summary:
		//	Called when we go on- or off-line
		// description:
		//	When we go online or offline, this method is called to update
		//	our UI. Default behavior is to update the Offline
		//	Widget UI and to attempt a synchronization.
		// type: String
		//	"online" if we just moved online, and "offline" if we just
		//	moved offline.
		
		if(!this._initialized){ return; }
		
		// update UI
		this._updateNetIndicator();
		
		if(type == "offline"){
			this._setSyncMessage("You are working offline");
		
			// clear old details
			var details = dojo.byId("dot-sync-details");
			if(details){ details.style.display = "none"; }
			
			// if we fell offline during a sync, hide
			// the sync info
			this._updateSyncUI();
		}else{ // online
			// synchronize, but pause for a few seconds
			// so that the user can orient themselves
			if(dojox.off.sync.autoSync){
				if(dojo.isAIR){
					window.setTimeout(function(){dojox.off.sync.synchronize();}, 1000);
				}else{
					window.setTimeout(dojox._scopeName + ".off.sync.synchronize()", 1000);
				}
			}
		}
	}
});

// register ourselves for low-level framework events
dojo.connect(dojox.off, "onFrameworkEvent", dojox.off.ui, "_onFrameworkEvent");

// start our magic when the Dojo Offline framework is ready to go
dojo.connect(dojox.off, "onLoad", dojox.off.ui, dojox.off.ui._initialize);

}

if(!dojo._hasResource["dojox.off.offline"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.off.offline"] = true;
dojo.provide("dojox.off.offline");







}

