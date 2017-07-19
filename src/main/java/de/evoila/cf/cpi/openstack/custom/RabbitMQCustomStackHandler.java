/**
 * 
 */
package de.evoila.cf.cpi.openstack.custom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.openstack4j.model.heat.Stack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import de.evoila.cf.broker.bean.OpenstackBean;
import de.evoila.cf.broker.custom.rabbitmq.RabbitMqCustomImplementation;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.ServerAddress;
import de.evoila.cf.broker.persistence.mongodb.repository.ClusterStackMapping;
import de.evoila.cf.broker.persistence.mongodb.repository.StackMappingRepository;
import de.evoila.cf.cpi.openstack.custom.cluster.ClusterStackHandler;

/**
 * @author Christian Mueller, evoila
 *
 */
@Service
@ConditionalOnBean(OpenstackBean.class)
public class RabbitMQCustomStackHandler extends ClusterStackHandler {
	
	private static final String NAME_TEMPLATE = "rabbitmq-%s-%s";
	
	protected static final String PRIMARY_TEMPLATE = "/openstack/master.yaml";
	protected static final String SECONDARY_TEMPLATE = "/openstack/mirrors.yaml";
	
	private String keyPair;
	
	private String subnetId;
	
	private final Logger log = LoggerFactory.getLogger(RabbitMQCustomStackHandler.class);

	@Autowired
	private StackMappingRepository stackMappingRepo;
	
	@Autowired
	private OpenstackBean openstackBean;
	
	@PostConstruct
	private void initValues() {
		keyPair = openstackBean.getKeypair();
		subnetId = openstackBean.getSubnetId();
	}
	
	public RabbitMQCustomStackHandler() {
		super();
		log.info("Cluster Server");
	}

	@Override
	public void delete(String internalId) {
		ClusterStackMapping stackMapping;
		stackMapping = stackMappingRepo.findOne(internalId);

		if (stackMapping == null) {
			super.delete(internalId);
		} else {
			try {
				super.deleteAndWait(stackMapping.getPrimaryStack());
				Thread.sleep(20000);
			} catch (PlatformException | InterruptedException e) {
				log.error("Could not delete Stack " + stackMapping.getPrimaryStack() + " Instance " + internalId);
				log.error(e.getMessage());
			}
			for(String stack : stackMapping.getSecondaryStacks()) {
				super.delete(stack);
			}
			super.delete(stackMapping.getPortsStack());
			super.delete(stackMapping.getVolumeStack());

			stackMappingRepo.delete(stackMapping);
		}
	}


	@Override
	protected String createCluster(String instanceId, Map<String, String> customParameters)
			throws PlatformException, InterruptedException {

		log.debug("Start create a RabbitMQ cluster");
		customParameters.putAll(defaultParameters());
		customParameters.putAll(generateValues(instanceId));
		
		ClusterStackMapping stackMapping = new ClusterStackMapping();
		stackMapping.setId(instanceId);
		
		Stack preIpStack = createPreIpStack(instanceId, customParameters);
		stackMapping.setPortsStack(preIpStack.getId());
		stackMappingRepo.save(stackMapping);
		
		List<String>[] responses = extractResponses(preIpStack, PORTS_KEY, IP_ADDRESS_KEY);
		List<String> ips = responses[1];
		List<String> ips_clone = new ArrayList<String>(ips);
		List<String> ports = responses[0];
		
		for(int i = 0; i < ips.size(); i++) {
			String ip = ips.get(i);
			stackMapping.addServerAddress(new ServerAddress("node-" + i, ip, 5672));
			stackMapping.addServerAddress(new ServerAddress("user", ip, 15672));
		}
		
		RabbitMqParameterManager.updatePortParameters(customParameters, ips, ports);
		Stack preVolumeStack = createPreVolumeStack(instanceId, customParameters);
		stackMapping.setVolumeStack(preVolumeStack.getId());
		stackMappingRepo.save(stackMapping);
		
		responses = extractResponses(preVolumeStack, VOLUME_KEY);
		List<String> volumes = responses[0];
		RabbitMqParameterManager.updateVolumeParameters(customParameters, volumes);
		
		Stack mainStack = createMainStack(instanceId, customParameters);
		stackMapping.setPrimaryStack(mainStack.getId());
		stackMappingRepo.save(stackMapping);
		
		
		Stack loadBalancer = createLoadStack(instanceId, customParameters, ips_clone);
		stackMapping.addSecondaryStack(loadBalancer.getId());
		
		setHaProxyPolicy(stackMapping);
		
		String ip = extractSingleValueResponses(loadBalancer , "vip_address")[0];
		stackMapping.addServerAddress(new ServerAddress("default", ip, 5672));
		
		

		stackMappingRepo.save(stackMapping);
		
		return stackMapping.getId();
	}
	


	private void setHaProxyPolicy(ClusterStackMapping stackMapping) {
		RabbitMqCustomImplementation rabbitMqImplementation = new RabbitMqCustomImplementation();
		ServerAddress apiAddress = null;
		for(ServerAddress address : stackMapping.getServerAddresses()) {
			if(address.getName().equals("user")) {
				apiAddress = address;
				break;
			}
		}
		if(apiAddress != null) {
			int port = apiAddress.getPort();
			rabbitMqImplementation.setAdminPort(port);
			rabbitMqImplementation.setHaPolicy(apiAddress.getIp(), port, stackMapping.getId(), stackMapping.getId(), stackMapping.getId());
			return; 
		}
	}

	private Stack createLoadStack(String instanceId, Map<String, String> customParameters,List<String> ips_clone) throws PlatformException {
		Map<String, String> parameters = new HashMap<String,String>();
		String name = String.format(NAME_TEMPLATE, instanceId, "loadbalancer");
		parameters.put("name", name);
		parameters.put("subnet", subnetId);
		parameters.put("port", Integer.toString(5672));
		parameters.put("addresses", RabbitMqParameterManager.join(ips_clone));

		String loadBalancer = accessTemplate("/openstack/loadbalancer.yml");
		heatFluent.create(name, loadBalancer, parameters, false, 10l);
		Stack stack = stackProgressObserver.waitForStackCompletion(name);
		

		return stack;
	}

	private Map<? extends String, ? extends String> generateValues(String instanceId) {
		HashMap<String, String> valueMap = new HashMap<String, String>();
		valueMap.put(RabbitMqParameterManager.ADMIN_USER, instanceId);
		valueMap.put(RabbitMqParameterManager.ADMIN_PASSWORD, instanceId);
		valueMap.put(RabbitMqParameterManager.RABBITMQ_VHOST, instanceId);
		valueMap.put(RabbitMqParameterManager.KEY_NAME, keyPair);
		return valueMap;
	}

	private Stack createPreIpStack(String instanceId, Map<String, String> customParameters) throws PlatformException {
		Map<String, String> parametersPreIp = RabbitMqParameterManager.copyProperties(customParameters,
				RabbitMqParameterManager.NODE_NUMBER,
				RabbitMqParameterManager.RESOURCE_NAME,
				RabbitMqParameterManager.NETWORK_ID,
				RabbitMqParameterManager.SECURITY_GROUPS);
		
		String name = String.format(NAME_TEMPLATE, instanceId, "ip");
		parametersPreIp.put(RabbitMqParameterManager.RESOURCE_NAME, name);
		
		String templatePorts = accessTemplate(PRE_IP_TEMPLATE);
		
		heatFluent.create(name, templatePorts, parametersPreIp, false, 10l);
		
		Stack preIpStack = stackProgressObserver.waitForStackCompletion(name);
		return preIpStack;
	}
	
	private Stack createPreVolumeStack(String instanceId, Map<String, String> customParameters) throws PlatformException {
		Map<String, String> parametersPreVolume = RabbitMqParameterManager.copyProperties(customParameters,
				RabbitMqParameterManager.NODE_NUMBER,
				RabbitMqParameterManager.RESOURCE_NAME,
				RabbitMqParameterManager.VOLUME_SIZE);
		
		String name = String.format(NAME_TEMPLATE, instanceId, "vol");
		parametersPreVolume.put(RabbitMqParameterManager.RESOURCE_NAME, name);
		
		String templatePorts = accessTemplate(PRE_VOLUME_TEMPLATE);
		heatFluent.create(name, templatePorts, parametersPreVolume, false, 10l);
		Stack preVolumeStack = stackProgressObserver.waitForStackCompletion(name);
		return preVolumeStack;
	}
	
	private Stack createMainStack(String instanceId, Map<String, String> customParameters) throws PlatformException {
		Map<String, String> parametersMain = RabbitMqParameterManager.copyProperties(customParameters,
				RabbitMqParameterManager.IMAGE_ID,
				RabbitMqParameterManager.KEY_NAME,
				RabbitMqParameterManager.FLAVOR,
				RabbitMqParameterManager.AVAILABILITY_ZONE,
				RabbitMqParameterManager.RESOURCE_NAME,
				RabbitMqParameterManager.MASTER_VOLUME_ID,
				RabbitMqParameterManager.MASTER_PORT,
				RabbitMqParameterManager.MASTER_IP,
				RabbitMqParameterManager.MIRROR1_VOLUME_ID,
				RabbitMqParameterManager.MIRROR1_PORT,
				RabbitMqParameterManager.MIRROR1_IP,
				RabbitMqParameterManager.MIRROR2_VOLUME_ID,
				RabbitMqParameterManager.MIRROR2_PORT,
				RabbitMqParameterManager.MIRROR2_IP,
				RabbitMqParameterManager.RABBITMQ_VHOST,
				RabbitMqParameterManager.ADMIN_USER,
				RabbitMqParameterManager.ADMIN_PASSWORD,
				RabbitMqParameterManager.ERLANG_KEY
				);
		
		String name = String.format(NAME_TEMPLATE, instanceId, "cl");
		parametersMain.put(RabbitMqParameterManager.RESOURCE_NAME, name);
		
		String template = accessTemplate(MAIN_TEMPLATE);
		String primary = accessTemplate(PRIMARY_TEMPLATE);
		String secondaries = accessTemplate(SECONDARY_TEMPLATE);
		
		Map<String, String> files = new HashMap<String, String>();
		files.put("master.yaml", primary);
		files.put("mirrors.yaml", secondaries);
		
		heatFluent.create(name, template, parametersMain, false, 10l, files);
		Stack stack = stackProgressObserver.waitForStackCompletion(name);
		return stack;
	}
	

}
