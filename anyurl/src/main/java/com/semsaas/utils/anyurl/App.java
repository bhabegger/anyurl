package com.semsaas.utils.anyurl;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {
	static Logger logger = LoggerFactory.getLogger(App.class);
	
	public static void main(String[] rawArgs) throws Exception {
		Properties props = new Properties();
		String args[] = processOption(rawArgs, props);
		
		String outputFile = props.getProperty("output");
		OutputStream os = outputFile == null ? System.out : new FileOutputStream(outputFile);
	
		final org.apache.camel.spring.Main main = new org.apache.camel.spring.Main();
		main.setApplicationContextUri("classpath:application.xml");
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
		    public void run() { try {
				main.stop();
			} catch (Exception e) {} }
		});


		if(args.length > 0) {
			main.start();
			if(main.isStarted()) {
				CamelContext camelContext = main.getCamelContexts().get(0);
				
				String target = rewriteEndpoint(args[0]);
				boolean producerBased = checkProducerBased(target);
				
				
				InputStream is = null;
				if(producerBased) {
				ProducerTemplate producer = camelContext.createProducerTemplate();
					is = producer.requestBody(target, null, InputStream.class);
				} else {
				ConsumerTemplate consumer = camelContext.createConsumerTemplate();
					is = consumer.receiveBody(target, InputStream.class);
				}
				IOUtils.copy(is, os);
				
				main.stop();
			} else {
				System.err.println("Couldn't trigger jobs, camel wasn't started");
			}
		} else {
			logger.info("No triggers. Running indefintely");
		}
	}

	private static boolean checkProducerBased(String target) {
		return target.startsWith("http");
	}

	private static String rewriteEndpoint(String target) {
		return target.replaceFirst("^http:", "http4:")
				.replaceFirst("^https:", "https4:");
	}

	private static String[] processOption(String[] args, Properties props) {
		LongOpt[] options = new LongOpt[] {
				new LongOpt("output", LongOpt.REQUIRED_ARGUMENT,null, 'o')
		};
		
		// Build auxilary structures
		HashMap<Integer, LongOpt> shortOptionMap = new HashMap<Integer, LongOpt>();
		StringBuffer decl = new StringBuffer();
		for(LongOpt o: options) {
			shortOptionMap.put(o.getVal(),o);
			decl.append((char)o.getVal());
			if(o.getHasArg() == LongOpt.OPTIONAL_ARGUMENT) {
				decl.append("::");
			} else if (o.getHasArg() == LongOpt.REQUIRED_ARGUMENT) {
				decl.append(":");
			}
		}
		Getopt g = new Getopt("anyurl", args, decl.toString(), options);
		 
		int c= 0;
		while ((c = g.getopt()) != -1) {
			LongOpt opt = shortOptionMap.get(c);
			String optName = opt.getName();
			String optVal = g.getOptarg();
			props.put(optName, optVal);
		}
		
		// NB: Getopt moves non options to the end
		return Arrays.copyOfRange(args, g.getOptind(), args.length);
	}
}
