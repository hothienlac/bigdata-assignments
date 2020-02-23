package edu.ttu.hothienlac.controllers;

import org.slf4j.Logger;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sap.cloud.sdk.cloudplatform.logging.CloudLoggerFactory;
import com.sap.cloud.mkt.services.facade.MarketingServiceFactory;

import edu.ttu.hothienlac.models.HelloWorldResponse;

@RestController
@RequestMapping( "/hello" )
public class HelloWorldController
{
    private static final Logger logger = CloudLoggerFactory.getLogger(HelloWorldController.class);

    @RequestMapping( method = RequestMethod.GET )
    public ResponseEntity<HelloWorldResponse> getHello( @RequestParam( defaultValue = "world" ) final String name )
    {
        logger.info("I am running!");
        return ResponseEntity.ok(new HelloWorldResponse(name));
    }
    
    @RequestMapping(path = "/getContacts", method = RequestMethod.GET)
    public ResponseEntity<?> getContacts() {
        return ResponseEntity.ok(MarketingServiceFactory.getContactService().getContacts(20,0));
    }
}
