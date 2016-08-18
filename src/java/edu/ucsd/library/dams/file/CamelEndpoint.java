package edu.ucsd.library.dams.file;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

/**
 * Abstract class for camel endpoint invocation
 * @author lsitu
 */
public abstract class CamelEndpoint {

	private static Logger LOGGER = Logger.getLogger(Endpoint.class);
	protected Endpoint endpoint = null;

	protected CamelEndpoint (Endpoint endpoint) {
		this.endpoint = endpoint;
	}

	protected boolean invoke (Exchange exchange) throws Exception {
		Producer producer = null;
		try {
	        // to send the exchange we need an producer to do it for us
	        producer = endpoint.createProducer();

	        // start the producer so it can operate
            producer.start();

            // let the producer process the exchange where it does all the work in this one line of code
            producer.process(exchange);

            // get the response from the out body message
            String response = exchange.getOut().getBody(String.class);
     
            LOGGER.info("Process result is " + exchange.getOut().getHeader("result") + ": " + response + "\n");
            String result = "" + exchange.getOut().getHeader("result");
            if (StringUtils.isBlank(result) || !Boolean.valueOf(result)) {
                if(exchange.getException() != null)
                    throw exchange.getException();
                return false;
            }

            return (boolean) exchange.getOut().getHeader("result");
        } finally {
            if ( producer != null ) {
                // stopping the JMS producer has the side effect of the "ReplyTo Queue" being properly
                // closed, making this client not to try any further reads for the replies from the server
            	producer.stop();
            }
        }
	}
}
