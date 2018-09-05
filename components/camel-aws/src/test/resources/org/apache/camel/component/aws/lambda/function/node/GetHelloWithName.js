'use strict';
exports.handler = function(event, context, callback) {
	var name = (event.name === undefined ? 'No-Name' : event.name);
	callback(null, {"Hello":name});
}
