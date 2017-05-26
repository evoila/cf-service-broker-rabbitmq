/**
 * 
 */
package de.evoila.cf.cpi.openstack.custom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openstack4j.model.heat.Stack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.ServerAddress;

/**
 * @author Christian Mueller, evoila
 *
 */
@Service
@ConditionalOnProperty(prefix = "openstack", name = { "keypair "}, havingValue = "")
public class RabbitMQCustomStackHandler extends CustomStackHandler {

	private static final String PRE_IP_TEMPLATE = "/openstack/pre-ips.yaml";
	private static final String PRE_VOLUME_TEMPLATE = "/openstack/pre-volume.yaml";
	
	private static final String MAIN_TEMPLATE = "/openstack/main.yaml";
	
	private static final String PRIMARY_TEMPLATE = "/openstack/primary.yaml";
	private static final String SECONDARY_TEMPLATE = "/openstack/secondaries.yaml";
	
	private static final String NAME_TEMPLATE = "rabbitmq-%s-%s";
	
	private static final String PORTS_KEY = "port_ids";
	private static final String IP_ADDRESS_KEY = "port_ips";
	private static final String VOLUME_KEY = "volume_ids";
	
	@Value("${openstack.keypair}")
	private String keyPair;
	
	private final Logger log = LoggerFactory.getLogger(RabbitMQCustomStackHandler.class);

	@Autowired
	private StackMappingRepository stackMappingRepo;
	
	public RabbitMQCustomStackHandler() {
		super();
	}

	@Override
	public void delete(String internalId) {
		RabbitMQStackMapping stackMapping = stackMappingRepo.findOne(internalId);
		
		if(stackMapping == null) {
			super.delete(internalId);
		} else {
			List<String> secondaryStacks = stackMapping.getSecondaryStacks();
			for(String stackId : secondaryStacks) {
				super.delete(stackId);
			}
			
			super.delete(stackMapping.getPrimaryStack());
			super.delete(stackMapping.getPortsStack());
			
			stackMappingRepo.delete(stackMapping);
		}
	}

	@Override
	public String create(String instanceId, Map<String, String> customParameters)
			throws PlatformException, InterruptedException {
		log.debug(customParameters.toString());
		if(customParameters.containsKey(ParameterManager.CLUSTER)) {
			log.debug("Start creating cluster " + instanceId);
			RabbitMQStackMapping clusterStacks = createCluster(instanceId, customParameters);
			log.debug("End creating cluster " + instanceId);
			stackMappingRepo.save(clusterStacks);
			return clusterStacks.getId();
		}
		log.debug("Not creating cluster " + instanceId);
		return super.create(instanceId, customParameters);
	}

	private RabbitMQStackMapping createCluster(String instanceId, Map<String, String> customParameters)
			throws PlatformException, InterruptedException {

		log.debug("Start create a RabbitMQ cluster");
		customParameters.putAll(defaultParameters());
		customParameters.putAll(generateValues(instanceId));
		
		RabbitMQStackMapping stackMapping = new RabbitMQStackMapping();
		stackMapping.setId(instanceId);
		
		Stack preIpStack = createPreIpStack(instanceId, customParameters);
		stackMapping.setPortsStack(preIpStack.getId());
		stackMappingRepo.save(stackMapping);
		
		List<String>[] responses = extractResponses(preIpStack, PORTS_KEY, IP_ADDRESS_KEY);
		List<String> ips = responses[1];
		List<String> ports = responses[0];
		
		for(int i = 0; i < ips.size(); i++) {
			String ip = ips.get(i);
			stackMapping.addServerAddress(new ServerAddress("node-" + i, ip, 5672));
		}
		
		ParameterManager.updatePortParameters(customParameters, ips, ports);
		Stack preVolumeStack = createPreVolumeStack(instanceId, customParameters);
		stackMapping.setVolumeStack(preVolumeStack.getId());
		stackMappingRepo.save(stackMapping);
		
		responses = extractResponses(preVolumeStack, VOLUME_KEY);
		List<String> volumes = responses[0];
		ParameterManager.updateVolumeParameters(customParameters, volumes);
		
		Stack mainStack = createMainStack(instanceId, customParameters);
		stackMapping.setPrimaryStack(mainStack.getId());
		stackMappingRepo.save(stackMapping);
		
		return stackMapping;
	}
	
	private Map<? extends String, ? extends String> generateValues(String instanceId) {
		HashMap<String, String> valueMap = new HashMap<String, String>();
		valueMap.put(ParameterManager.ADMIN_USER, instanceId);
		valueMap.put(ParameterManager.ADMIN_PASSWORD, instanceId);
		valueMap.put(ParameterManager.RABBITMQ_VHOST, instanceId);
		valueMap.put(ParameterManager.KEY_NAME, keyPair);
		return valueMap;
	}

	private Stack createPreIpStack(String instanceId, Map<String, String> customParameters) throws PlatformException {
		Map<String, String> parametersPreIp = ParameterManager.copyProperties(customParameters,
				ParameterManager.NODE_NUMBER,
				ParameterManager.RESOURCE_NAME,
				ParameterManager.NETWORK_ID,
				ParameterManager.SECURITY_GROUPS);
		
		String name = String.format(NAME_TEMPLATE, instanceId, "ip");
		parametersPreIp.put(ParameterManager.RESOURCE_NAME, name);
		
		String templatePorts = accessTemplate(PRE_IP_TEMPLATE);
		
		heatFluent.create(name, templatePorts, parametersPreIp, false, 10l);
		
		Stack preIpStack = stackProgressObserver.waitForStackCompletion(name);
		return preIpStack;
	}
	
	private Stack createPreVolumeStack(String instanceId, Map<String, String> customParameters) throws PlatformException {
		Map<String, String> parametersPreVolume = ParameterManager.copyProperties(customParameters,
				ParameterManager.NODE_NUMBER,
				ParameterManager.RESOURCE_NAME,
				ParameterManager.VOLUME_SIZE);
		
		String name = String.format(NAME_TEMPLATE, instanceId, "vol");
		parametersPreVolume.put(ParameterManager.RESOURCE_NAME, name);
		
		String templatePorts = accessTemplate(PRE_VOLUME_TEMPLATE);
		heatFluent.create(name, templatePorts, parametersPreVolume, false, 10l);
		Stack preVolumeStack = stackProgressObserver.waitForStackCompletion(name);
		return preVolumeStack;
	}
	
	private Stack createMainStack(String instanceId, Map<String, String> customParameters) throws PlatformException {
		Map<String, String> parametersMain = ParameterManager.copyProperties(customParameters,
				ParameterManager.IMAGE_ID,
				ParameterManager.KEY_NAME,
				ParameterManager.FLAVOR,
				ParameterManager.AVAILABILITY_ZONE,
				ParameterManager.RESOURCE_NAME,
				ParameterManager.MASTER_VOLUME_ID,
				ParameterManager.MASTER_PORT,
				ParameterManager.MASTER_IP,
				ParameterManager.MIRROR1_VOLUME_ID,
				ParameterManager.MIRROR1_PORT,
				ParameterManager.MIRROR1_IP,
				ParameterManager.MIRROR2_VOLUME_ID,
				ParameterManager.MIRROR2_PORT,
				ParameterManager.MIRROR2_IP,
				ParameterManager.RABBITMQ_VHOST,
				ParameterManager.ADMIN_USER,
				ParameterManager.ADMIN_PASSWORD,
				ParameterManager.ERLANG_KEY);
		
		String name = String.format(NAME_TEMPLATE, instanceId, "cl");
		parametersMain.put(ParameterManager.RESOURCE_NAME, name);
		
		String template = accessTemplate(MAIN_TEMPLATE);
		String primary = accessTemplate(PRIMARY_TEMPLATE);
		String secondaries = accessTemplate(SECONDARY_TEMPLATE);
		
		Map<String, String> files = new HashMap<String, String>();
		files.put("primary.yaml", primary);
		files.put("secondaries.yaml", secondaries);
		
		heatFluent.create(name, template, parametersMain, false, 10l, files);
		Stack stack = stackProgressObserver.waitForStackCompletion(name);
		return stack;
	}
	
	private List<String>[] extractResponses(Stack stack, String... keys) {
		List<String>[] response = new List[keys.length];
		
		for(Map<String, Object> output : stack.getOutputs()) {
			Object outputKey = output.get("output_key");
			if(outputKey!= null && outputKey instanceof String) {
				String key = (String) outputKey;
				for(int i = 0; i < keys.length; i++) {
					if(key.equals(keys[i])) {
						response[i] = (List<String>) output.get("output_value");
					}
				}
			}
		}
		return response;
	}
}
