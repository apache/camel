dojo.require("dojo.io.script")
dojo.require("dojox.rpc.Service");

// model that works with Yahoo Search API
dojo.declare("dojox.grid.data.yahooSearch", dojox.grid.data.Dynamic, {
	constructor: function(inFields, inData, inSearchNode){
		this.rowsPerPage = 20;
		this.searchNode = inSearchNode;
		this.fieldNames = dojo.map(inFields, "return item.name;");
		this.yahoo = new dojox.rpc.Service(
			dojo.moduleUrl("dojox.rpc.SMDLibrary", "yahoo.smd")
		);
	},
	// server send / receive
	send: function(inAsync, inParams, inOnReceive, inOnError){
		var d = this.yahoo.imageSearch(
			dojo.mixin({ 
				results: this.rowsPerPage,
				query: this.getQuery()
			}, inParams)
		);
		d.addCallbacks(
			dojo.hitch(this, "receive", inOnReceive, inOnError), 
			dojo.hitch(this, "error", inOnError)
		);
		this.onSend(inParams);
		return d;
	},
	receive: function(inOnReceive, inOnError, inData){
		try{
			inData = inData.ResultSet;
			inOnReceive(inData);
			this.onReceive(inData);
		}catch(e){
			if(inOnError){
				inOnError(inData);
			}
		}
	},
	error: function(inOnError, inErr) {
		var m = 'io error: ' + inErr.message;
		alert(m);
		if (inOnError)
			inOnError(m);
	},
	encodeParam: function(inName, inValue) {
		return dojo.string.substitute('&${0}=${1}', [inName, inValue]);
	},
	getQuery: function(){
		return dojo.byId(this.searchNode).value.replace(/ /g, '+');
	},
	fetchRowCount: function(inCallback){
		this.send(true, inCallback);
	},
	// request data 
	requestRows: function(inRowIndex, inCount){
		inRowIndex = (inRowIndex == undefined ? 0 : inRowIndex);
		var params = { 
			start: inRowIndex + 1
		}
		this.send(true, params, dojo.hitch(this, this.processRows));
	},
	// server callbacks
	processRows: function(inData){
		for(var i=0, l=inData.totalResultsReturned, s=inData.firstResultPosition; i<l; i++){
			this.setRow(inData.Result[i], s - 1 + i);
		}
		// yahoo says 1000 is max results to return
		var c = Math.min(1000, inData.totalResultsAvailable);
		if(this.count != c){
			this.setRowCount(c);
			this.allChange();
			this.onInitializeData(inData);
		}
	},
	getDatum: function(inRowIndex, inColIndex){
		var row = this.getRow(inRowIndex);
		var field = this.fields.get(inColIndex);
		return (inColIndex == undefined ? row : (row ? row[field.name] : field.na));
	},
	// events
	onInitializeData: function(){ },
	onSend: function(){ },
	onReceive: function(){ }
});

// report
modelChange = function(){
	var n = dojo.byId('rowCount');
	if(n){
		n.innerHTML = dojo.string.substitute('about ${0} row(s)', [model.count]);
	}
}


// some data formatters
getCellData = function(inCell, inRowIndex, inField){
	var m = inCell.grid.model;
	return m.getDatum(inRowIndex, inField);
}

formatLink = function(inData, inRowIndex){
	if(!inData){ return '&nbsp;'; }
	var text = getCellData(this, inRowIndex, this.extraField);
	return dojo.string.substitute(
		'<a target="_blank" href="${href}">${text}</a>', 
		{ href: inData, text: text }
	);
};

formatImage = function(inData, inRowIndex){
	if(!inData){ return '&nbsp;'; }
	var info = getCellData(this, inRowIndex, this.extraField);
	var o = {
		href: inData, 
		src: info.Url,
		width: info.Width,
		height: info.Height
	}
	return dojo.string.substitute(
		'<a href="${href}" target="_blank"><img border=0 src="${src}" width="${width}" height="${height}"></a>', o);
};

formatDate = function(inDatum, inRowIndex){
	if(!inDatum){ return '&nbsp;'; }
	var d = new Date(inDatum * 1000);
	return dojo.string.substitute(
		"${0}/${1}/${2}",
		[ d.getMonth()+1, d.getDate(), d.getFullYear() ]
	);
};

formatDimensions = function(inData, inRowIndex){
	if(!inData){ return '&nbsp;'; }
	var w = inData, h = getCellData(this, inRowIndex, this.extraField);
	return w + ' x ' + h;
}
