package org.hobbit.benchmark.faceted_browsing.main;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.qpid.server.Broker;
import org.apache.qpid.server.BrokerOptions;
import org.hobbit.benchmark.faceted_browsing.config.ConfigHobbitLocalServices;
import org.hobbit.benchmark.faceted_browsing.config.HobbitConfigLocalPlatformFacetedBenchmark;
import org.hobbit.core.Commands;
import org.hobbit.core.component.PseudoHobbitPlatformController;
import org.hobbit.core.config.HobbitConfigChannelsPlatform;
import org.hobbit.core.config.HobbitConfigCommon;
import org.hobbit.core.utils.ServiceManagerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePropertySource;

import com.google.common.util.concurrent.Service;
import com.rabbitmq.client.Channel;

public class MainHobbitFacetedBrowsingBenchmarkRemote
{
	private static final Logger logger = LoggerFactory.getLogger(MainHobbitFacetedeBrowsingBenchmarkLocal.class);

	protected static StandardEnvironment environment = new StandardEnvironment();
	
	/**
	 * If the resource is a file, returns the corresponding file object.
	 * Otherwise, creates a temporary file and copies the resource's data into it.
	 * 
	 * 
	 * @param resource
	 * @param prefix
	 * @param suffix
	 * @return
	 * @throws IOException
	 */
    public static File getResourceAsFile(Resource resource, String prefix, String suffix) throws IOException {
    	File result;
    	try {
    		result = resource.getFile();
    	} catch(Exception e) {
    	
	    	//FileCopyUtils.copy(in, out)
	    	result = File.createTempFile(prefix, suffix);
	    	Files.copy(resource.getInputStream(), result.toPath(), StandardCopyOption.REPLACE_EXISTING);
	    	result.deleteOnExit();
    	}
    	
    	return result;
    }

	public static Broker start() throws Exception {
		//Map<String, String> environment = System.getenv();
		
		environment.getPropertySources().addFirst(new ResourcePropertySource("classpath:/local-config.properties"));
		
		
        String amqpInitialConfigUrl = getResourceAsFile(new ClassPathResource("amqp-initial-config.json"), "amqp-config-", ".json").getAbsoluteFile().toURI().toURL().toString();

	    Broker broker = new Broker();
	    BrokerOptions brokerOptions = new BrokerOptions();
	    
//	    brokerOptions.setConfigProperty('qpid.amqp_port',"${amqpPort}")
//	    brokerOptions.setConfigProperty('qpid.http_port', "${httpPort}")
//	    brokerOptions.setConfigProperty('qpid.home_dir', homePath);

	    brokerOptions.setInitialConfigurationLocation(amqpInitialConfigUrl); //"classpath:/amqp-initial-config.json");
	    brokerOptions.setConfigProperty("qpid.amqp_port", environment.getProperty("spring.amqp.port"));
	    brokerOptions.setConfigProperty("qpid.broker.defaultPreferenceStoreAttributes", "{\"type\": \"Noop\"}");
	    brokerOptions.setConfigProperty("qpid.vhost", environment.getProperty("spring.amqp.vhost"));
	    brokerOptions.setConfigurationStoreType("Memory");
	    brokerOptions.setStartupLoggedToSystemOut(false);
	    broker.startup(brokerOptions);
	    //System.out.println("Broker starting...");
	    //Thread.sleep(5000);
	    return broker;
	}

	
    public static void main(String[] args) throws Exception {

    	try {
    		start(args);
    	} finally {
    		logger.info("Execution is done.");
    	}
    }

    public static void start(String[] args) throws Exception {
    	
    	Broker broker = start();

    	try {
    		run(args);
    	} finally {    	
    		broker.shutdown();
    	}
    }


    public static void run(String[] args) {
    	Service systemUnderTestService = null;

    	ConfigurableApplicationContext ctx = null;
    	try {
    		

	    	//SpringApplication.run(Application.class, args);
	        Properties props = new Properties();
	        props.put("spring.config.location", "classpath:/local-config.properties");
	        
	    	ctx = new SpringApplicationBuilder()
	    		.properties(props)
	    		.sources(HobbitConfigCommon.class)
	        	.sources(HobbitConfigLocalPlatformFacetedBenchmark.class)
	        	.sources(HobbitConfigChannelsPlatform.class)
	        	.sources(ConfigHobbitLocalServices.class)
	        	.bannerMode(Banner.Mode.OFF)
	        	.run(args);
	    	

//	    try(AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
//	            HobbitConfigLocalPlatformFacetedBenchmark.class,
//	            HobbitConfigChannelsPlatform.class,
//	            ConfigHobbitLocalServices.class)) {
	
	    	
    		systemUnderTestService = (Service)ctx.getBean("systemUnderTestService");
    		systemUnderTestService.startAsync();
    		systemUnderTestService.awaitRunning();
    		
//The system adapter has to send out the system_ready_signal
//    		WritableByteChannel commandChannel = (WritableByteChannel)ctx.getBean("commandChannel");
//    		commandChannel.write(ByteBuffer.wrap(new byte[] {Commands.SYSTEM_READY_SIGNAL}));
    		
    		
        	Supplier<Service> systemAdapterServiceFactory = (Supplier<Service>)ctx.getBean("systemAdapterServiceFactory");
        	Service systemAdapter = systemAdapterServiceFactory.get();
        	systemAdapter.startAsync();
        	systemAdapter.awaitRunning();
        	
        	
            PseudoHobbitPlatformController commandHandler = ctx.getBean(PseudoHobbitPlatformController.class);
            commandHandler.accept(ByteBuffer.wrap(new byte[] {Commands.START_BENCHMARK_SIGNAL}));
            
            // Sending the command blocks until the benchmark is complete
            //System.out.println("sent start benchmark signal");
            
    	} finally {
    		
            if(systemUnderTestService != null) {
                logger.debug("Shutting down system under test service");
                systemUnderTestService.stopAsync();
                ServiceManagerUtils.awaitTerminatedOrStopAfterTimeout(systemUnderTestService, 60, TimeUnit.SECONDS, 0, TimeUnit.SECONDS);
//              systemUnderTestService.stopAsync();
//              systemUnderTestService.awaitTerminated(60, TimeUnit.SECONDS);
            }
            
            if(ctx != null) {
            	 Map<String, Channel> channels = ctx.getBeansOfType(Channel.class);
            	 for(Entry<String, Channel> entry : channels.entrySet()) {
            		 Channel channel = entry.getValue();
            		 if(channel.isOpen()) {
            			 logger.warn("Closing still open channel " + entry.getKey());
            			 try {
            				 channel.close();
            			 } catch(Exception e) {
            				 logger.warn("Error closing channel", e);
            			 }
            		 }
            	 }
            }
    	}
    }
}