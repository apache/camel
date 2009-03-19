// example sample data and code
(function(){
	// some sample data
	// global var "data"
	data = [ 
		[ "normal", false, "new", 'But are not followed by two hexadecimal', 29.91, 10, false ],
		[ "important", false, "new", 'Because a % sign always indicates', 9.33, -5, false ],
		[ "important", false, "read", 'Signs can be selectively', 19.34, 0, true ],
		[ "note", false, "read", 'However the reserved characters', 15.63, 0, true ],
		[ "normal", false, "replied", 'It is therefore necessary', 24.22, 5.50, true ],
		[ "important", false, "replied", 'To problems of corruption by', 9.12, -3, true ],
		[ "note", false, "replied", 'Which would simply be awkward in', 12.15, -4, false ]
	];
	var rows = 100;
	for(var i=0, l=data.length; i<rows-l; i++){
		data.push(data[i%l].slice(0));
	}

	// global var "model"
	model = new dojox.grid.data.Table(null, data);

	// simple display of row info; based on model observation
	// global var "modelChange"
	modelChange = function(){
		var n = dojo.byId('rowCount');
		if(n){
			n.innerHTML = Number(model.getRowCount()) + ' row(s)';
		}
	}
})();
