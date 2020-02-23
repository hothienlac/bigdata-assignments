sap.ui.define([
	"sap/ui/core/mvc/Controller"
], function(Controller) {
	"use strict";

	return Controller.extend("com.sap.hybris.mkt.client.uiHybrisMktSDK-Client.controller.InitialView", {
		
		onInit: function() {
			
		},
		
		getRouter: function() {
			return this.getOwnerComponent().getRouter();
		}

	});
});