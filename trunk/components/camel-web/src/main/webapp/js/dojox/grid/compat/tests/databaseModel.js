if(!dojo._hasResource["dojox.grid.compat.tests.databaseModel"]){ //_hasResource checks added by build. Do not use _hasResource directly in your code.
dojo._hasResource["dojox.grid.compat.tests.databaseModel"] = true;
dojo.provide("dojox.grid.compat.tests.databaseModel");
dojo.require("dojox.grid.compat._data.model");

// Provides a sparse array that is also traversable inorder 
// with basic Array:
//   - iterating by index is slow for large sparse arrays
//   - for...in iteration is in order of element creation 
// maintains a secondary index for interating
// over sparse elements inorder
dojo.declare("dojox.grid.Sparse", null, {
	constructor: function() {
		this.clear();
	},
	clear: function() {
		this.indices = [];
		this.values = [];
	},
	length: function() {
		return this.indices.length;
	},
	set: function(inIndex, inValue) {
		for (var i=0,l=this.indices.length; i<l; i++) {
			if (this.indices[i] >= inIndex) 
				break;
		}
		if (this.indices[i] != inIndex) 
			this.indices.splice(i, 0, inIndex);
		this.values[inIndex] = inValue;
	},
	get: function(inIndex) {
		return this.values[inIndex];
	},
	remove: function(inIndex) {
		for (var i=0,l=this.indices.length; i<l; i++) 
			if (this.indices[i] == inIndex) {
				this.indices.splice(i, 1);
				break;
			}
		delete this.values[inIndex];
	},
	inorder: function(inFor) {
		for (var i=0,l=this.indices.length, ix; i<l; i++) {
			ix = this.indices[i];
			if (inFor(this.values[ix], ix) === false)
				break;
		}
	}
});

// sample custom model implementation that works with mysql server.
dojo.declare("dojox.grid.data.DbTable", dojox.grid.data.Dynamic, {
	delayedInsertCommit: true,
	constructor: function(inFields, inData, inServer, inDatabase, inTable) {
		this.server = inServer;
		this.database = inDatabase;
		this.table = inTable;
		this.stateNames = ['inflight', 'inserting', 'removing', 'error'];
		this.clearStates();
		this.clearSort();
	},
	clearData: function() {
		this.cache = [ ];
		this.clearStates();
		this.inherited(arguments);
	},
	clearStates: function() {
		this.states = {};
		for (var i=0, s; (s=this.stateNames[i]); i++) {
			delete this.states[s];
			this.states[s] = new dojox.grid.Sparse();
		}
	},
	// row state information
	getState: function(inRowIndex) {
		for (var i=0, r={}, s; (s=this.stateNames[i]); i++)
			r[s] = this.states[s].get(inRowIndex);
		return r;
	},
	setState: function(inRowIndex, inState, inValue) {
		this.states[inState].set(inRowIndex, inValue||true);
	},
	clearState: function(inRowIndex, inState) {
		if (arguments.length == 1) {
			for (var i=0, s; (s=this.stateNames[i]); i++)
				this.states[s].remove(inRowIndex);
		}	else {
			for (var i=1, l=arguments.length, arg; (i<l) &&((arg=arguments[i])!=undefined); i++)
				this.states[arg].remove(inRowIndex);
		}
	},
	setStateForIndexes: function(inRowIndexes, inState, inValue) {
		for (var i=inRowIndexes.length-1, k; (i>=0) && ((k=inRowIndexes[i])!=undefined); i--)
			this.setState(k, inState, inValue);
	},
	clearStateForIndexes: function(inRowIndexes, inState) {
		for (var i=inRowIndexes.length-1, k; (i>=0) && ((k=inRowIndexes[i])!=undefined); i--)
			this.clearState(k, inState);
	},
	//$ Return boolean stating whether or not an operation is in progress that may change row indexing.
	isAddRemoving: function() {
		return Boolean(this.states['inserting'].length() || this.states['removing'].length());
	},
	isInflight: function() {
		return Boolean(this.states['inflight'].length());
	},
	//$ Return boolean stating if the model is currently undergoing any type of edit.
	isEditing: function() {
		for (var i=0, r={}, s; (s=this.stateNames[i]); i++)
			if (this.states[s].length())
				return true;
	},
	//$ Return true if ok to modify the given row. Override as needed, using model editing state information.
	canModify: function(inRowIndex) {
		return !this.getState(inRowIndex).inflight && !(this.isInflight() && this.isAddRemoving());
	},
	// server send / receive
	getSendParams: function(inParams) {
		var p = {
			database: this.database || '',
			table: this.table || ''
		}
		return dojo.mixin(p, inParams || {});
	},
	send: function(inAsync, inParams, inCallbacks) {
		//console.log('send', inParams.command);
		var p = this.getSendParams(inParams);
		var d = dojo.xhrPost({
			url: this.server,
			content: p,
			handleAs: 'json-comment-filtered',
			contentType: "application/x-www-form-urlencoded; charset=utf-8",
			sync: !inAsync
		});
		d.addCallbacks(dojo.hitch(this, "receive", inCallbacks), dojo.hitch(this, "receiveError", inCallbacks));
		return d;
	},
	_callback: function(cb, eb, data) {
		try{ cb && cb(data); } 
		catch(e){ eb && eb(data, e); }
	},
	receive: function(inCallbacks, inData) {
		inCallbacks && this._callback(inCallbacks.callback, inCallbacks.errback, inData);
	},
	receiveError: function(inCallbacks, inErr) {
		this._callback(inCallbacks.errback, null, inErr)
	},
	encodeRow: function(inParams, inRow, inPrefix) {
		for (var i=0, l=inRow.length; i < l; i++)
			inParams['_' + (inPrefix ? inPrefix : '') + i] = (inRow[i] ? inRow[i] : '');
	},
	measure: function() {
		this.send(true, { command: 'info' }, { callback: dojo.hitch(this, this.callbacks.info) });
	},
	fetchRowCount: function(inCallbacks) {
		this.send(true, { command: 'count' }, inCallbacks);
	},
	// server commits
	commitEdit: function(inOldData, inNewData, inRowIndex, inCallbacks) {
		this.setState(inRowIndex, "inflight", true);
		var params = {command: 'update'};
		this.encodeRow(params, inOldData, 'o');
		this.encodeRow(params, inNewData);
		this.send(true, params, inCallbacks);
	},
	commitInsert: function(inRowIndex, inNewData, inCallbacks) {
		this.setState(inRowIndex, "inflight", true);
		var params = {command: 'insert'};
		this.encodeRow(params, inNewData);
		this.send(true, params, inCallbacks);
	},
	// NOTE: supported only in tables with pk
	commitDelete: function(inRows, inCallbacks) {
		var params = { 
			command: 'delete',
			count: inRows.length
		}	
		var pk = this.getPkIndex();
		if (pk < 0)
			return;
		for (var i=0; i < inRows.length; i++)	{
			params['_' + i] = inRows[i][pk];
		}	
		this.send(true, params, inCallbacks);
	},
	getUpdateCallbacks: function(inRowIndex) {
		return {
			callback: dojo.hitch(this, this.callbacks.update, inRowIndex), 
			errback: dojo.hitch(this, this.callbacks.updateError, inRowIndex)
		};
	},
	// primary key from fields
	getPkIndex: function() {
		for (var i=0, l=this.fields.count(), f; (i<l) && (f=this.fields.get(i)); i++)
			if (f.Key = 'PRI')
				return i;
		return -1;		
	},
	// model implementations
	update: function(inOldData, inNewData, inRowIndex) {
		var cbs = this.getUpdateCallbacks(inRowIndex);
		if (this.getState(inRowIndex).inserting)
			this.commitInsert(inRowIndex, inNewData, cbs);
		else
			this.commitEdit(this.cache[inRowIndex] || inOldData, inNewData, inRowIndex, cbs);
		// set push data immediately to model	so reflectd while committing
		this.setRow(inNewData, inRowIndex);
	},
	insert: function(inData, inRowIndex) {
		this.setState(inRowIndex, 'inserting', true);
		if (!this.delayedInsertCommit)
			this.commitInsert(inRowIndex, inData, this.getUpdateCallbacks(inRowIndex));
		return this.inherited(arguments);
	},
	remove: function(inRowIndexes) {
		var rows = [];
		for (var i=0, r=0, indexes=[]; (r=inRowIndexes[i]) !== undefined; i++)
			if (!this.getState(r).inserting) {
				rows.push(this.getRow(r));
				indexes.push(r);
				this.setState(r, 'removing');
			}
		var cbs = {
			callback: dojo.hitch(this, this.callbacks.remove, indexes),
			errback: dojo.hitch(this, this.callbacks.removeError, indexes)
		};
		this.commitDelete(rows, cbs);
		dojox.grid.data.Dynamic.prototype.remove.apply(this, arguments);
	},
	cancelModifyRow: function(inRowIndex) {
		if (this.isDelayedInsert(inRowIndex)) {
			this.removeInsert(inRowIndex);
		} else
			this.finishUpdate(inRowIndex);
	},	
	finishUpdate: function(inRowIndex, inData) {
		this.clearState(inRowIndex);
		var d = (inData&&inData[0]) || this.cache[inRowIndex];
		if (d)
			this.setRow(d, inRowIndex);
		delete this.cache[inRowIndex];
	},
	isDelayedInsert: function(inRowIndex) {
		return (this.delayedInsertCommit && this.getState(inRowIndex).inserting);
	},
	removeInsert: function(inRowIndex) {
		this.clearState(inRowIndex);
		dojox.grid.data.Dynamic.prototype.remove.call(this, [inRowIndex]);
	},
	// request data 
	requestRows: function(inRowIndex, inCount)	{
		var params = { 
			command: 'select',
			orderby: this.sortField, 
			desc: (this.sortDesc ? "true" : ''),
			offset: inRowIndex, 
			limit: inCount
		}
		this.send(true, params, {callback: dojo.hitch(this, this.callbacks.rows, inRowIndex)});
	},
	// sorting
	canSort: function () { 
		return true; 
	},
	setSort: function(inSortIndex) {
		this.sortField = this.fields.get(Math.abs(inSortIndex) - 1).name || inSortIndex;
		this.sortDesc = (inSortIndex < 0);
	},
	sort: function(inSortIndex) {
		this.setSort(inSortIndex);
		this.clearData();
	},
	clearSort: function(){
		this.sortField = '';
		this.sortDesc = false;
	},
	endModifyRow: function(inRowIndex){
		var cache = this.cache[inRowIndex];
		var m = false;
		if(cache){
			var data = this.getRow(inRowIndex);
			if(!dojox.grid.arrayCompare(cache, data)){
				m = true;
				this.update(cache, data, inRowIndex);
			}	
		}
		if (!m)
			this.cancelModifyRow(inRowIndex);
	},
	// server callbacks (called with this == model)
	callbacks: {
		update: function(inRowIndex, inData) {
			console.log('received update', arguments);
			if (inData.error)
				this.updateError(inData)
			else
				this.finishUpdate(inRowIndex, inData);
		},
		updateError: function(inRowIndex) {
			this.clearState(inRowIndex, 'inflight');
			this.setState(inRowIndex, "error", "update failed: " + inRowIndex);
			this.rowChange(this.getRow(inRowIndex), inRowIndex);
		},
		remove: function(inRowIndexes) {
			this.clearStateForIndexes(inRowIndexes);
		},
		removeError: function(inRowIndexes) {
			this.clearStateForIndexes(inRowIndexes);
			alert('Removal error. Please refresh.');
		},
		rows: function(inRowIndex, inData) {
			//this.beginUpdate();
			for (var i=0, l=inData.length; i<l; i++)
				this.setRow(inData[i], inRowIndex + i);
			//this.endUpdate();
			//this.allChange();
		},
		count: function(inRowCount) {
			this.count = Number(inRowCount);
			this.clearData();
		},
		info: function(inInfo) {
			this.fields.clear();
			for (var i=0, c; (c=inInfo.columns[i]); i++) {
				c.name = c.Field;
				this.fields.set(i, c);
			}
			this.table = inInfo.table;
			this.database = inInfo.database;
			this.notify("MetaData", arguments);
			this.callbacks.count.call(this, inInfo.count);
		}
	}
});

}
