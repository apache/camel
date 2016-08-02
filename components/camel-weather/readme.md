#Description of Weather API

##Types of queries

There are five type of queries:

1. current weather (contexts weather, group, find and box/city);
2. forecast (5 days per 3 hours, context forecast)
3. forecast/daily (context forecast/daily); you must provide cnt parameter
4. history (context history/city and maybe also history/station)
5. station (contexts station, box/station and station/find)

##Locations

1. by name of the city and the country code (q={city name},{country code})
2. by id of the city (id={city ID}); The current weather can also be
asked for a group of id's using the context group
3. by latitude and longitude (lat={lat}&lon={lon})
4. by box (bbox=12,32,15,37,10), you must also provide cluster=yes/no; context
must be box/city or box/station
5. find cities/station around location defined by lat/lon; context
must be find or station/find. You must provide a cnt parameter.


##Note
The history by station isn't documented at the OpenWeather website but
is mentioned in the issue