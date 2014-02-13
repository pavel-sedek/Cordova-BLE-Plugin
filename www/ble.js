var ble =  {
    listDevices: function( successCallback, errorCallback) {
        cordova.exec(
            successCallback, // success callback function
            errorCallback, // error callback function
            'BLEPlugin', // mapped to our native Java class called "Calendar"
            'listDevices', // with this action name
            [/*{                  // and this array of custom arguments to create our entry
                "title": title,
                "description": notes,
                "eventLocation": location,
                "startTimeMillis": startDate.getTime(),
                "endTimeMillis": endDate.getTime()
            }*/]
        );
    }
}
module.exports = ble;