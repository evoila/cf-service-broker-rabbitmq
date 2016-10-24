/**
 * 
 */
package de.evoila.cf.cpi.openstack.custom;

import java.util.List;
import java.util.Map;

import org.openstack4j.model.heat.Stack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.ServerAddress;

/**
 * @author Christian Mueller, evoila
 *
 */
@Service
public class RabbitMQCustomStackHandler extends CustomStackHandler {
	private final Logger log = LoggerFactory.getLogger(RabbitMQCustomStackHandler.class);

	@Value("${openstack.log_port}")
	private String logPort;

	@Value("${openstack.log_host}")
	private String logHost;

	@Autowired
	private StackMappingRepository stackMappingRepo;

	private ParameterManager paramManager = new ParameterManager(this);

	@Override
	public void delete(String internalId) {
		StackMapping stackMapping = stackMappingRepo.findOne(internalId);

		if (stackMapping == null) {
			super.delete(internalId);
		} else {
			List<String> secondaryStacks = stackMapping.getSecondaryStacks();
			for (String stackId : secondaryStacks) {
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

		if (customParameters.containsKey(ParameterManager.CLUSTER)) {
			StackMapping clusterStacks = createCluster(instanceId, customParameters);
			stackMappingRepo.save(clusterStacks);
			return clusterStacks.getId();
		} else
			return super.create(instanceId, customParameters);
	}

	/**
	 * @param instanceId
	 * @param customParameters
	 * @param plan
	 * @return
	 * @throws PlatformException
	 * @throws InterruptedException
	 */
	private StackMapping createCluster(String instanceId, Map<String, String> customParameters)
			throws PlatformException, InterruptedException {

		log.debug("Start create a rabbitMQ cluster");

		StackMapping stackMapping = new StackMapping();
		stackMapping.setId(instanceId);

		Integer secondaryNumber = paramManager.configureGeneralParameters(customParameters);

		Stack portsStack = createPortsStack(instanceId, customParameters);
		stackMapping.setPortsStack(portsStack.getId());
		stackMappingRepo.save(stackMapping);
		List<String> ips = null;
		List<String> ports = null;
		for (Map<String, Object> output : portsStack.getOutputs()) {
			Object outputKey = output.get("output_key");
			if (outputKey != null && outputKey instanceof String) {
				String key = (String) outputKey;
				if (key.equals("port_ips")) {
					ips = (List<String>) output.get("output_value");

				}
				if (key.equals("port_ports")) {
					ports = (List<String>) output.get("output_value");
				}
			}
		}

		for (int i = 0; i < ips.size(); i++) {
			String ip = ips.get(i);
			stackMapping.addServerAddress(new ServerAddress("node-" + i, ip));
		}
		stackMappingRepo.save(stackMapping);
		ParameterManager.updateGeneralParameters(customParameters, ips, ports);

		Stack primaryStack = createPrimaryStack(instanceId, customParameters);
		stackMapping.setPrimaryStack(primaryStack.getId());
		stackMappingRepo.save(stackMapping);

		Map<String, String> parametersSecondary = ParameterManager.copyProperties(customParameters,
				ParameterManager.RABBIT_VHOST, ParameterManager.RABBIT_USER, ParameterManager.RABBIT_PASSWORD, LOG_HOST,
				LOG_PORT, ParameterManager.ERLANG_KEY, ParameterManager.FLAVOR, ParameterManager.VOLUME_SIZE,
				ParameterManager.ETC_HOSTS, ParameterManager.MASTER_HOSTNAME, AVAILABILITY_ZONE, KEYPAIR, IMAGE_ID);

		String templateSec = accessTemplate("/openstack/templateSecondaries.yaml");

		for (int i = 0; i < secondaryNumber; i++) {
			createSecondaryStack(instanceId, ips, ports, parametersSecondary, templateSec, i);
		}

		for (int i = 0; i < secondaryNumber; i++) {
			Stack secondaryStack = stackProgressObserver.waitForStackCompletion("s" + instanceId + "_Sec" + i);
			stackMapping.addSecondaryStack(secondaryStack.getId());
			stackMappingRepo.save(stackMapping);
		}

		log.debug("Stack deployment for RabbitMQ ready - Stacks:" + stackMapping.getSecondaryStacks().size() + 2);

		return stackMapping;
	}

	private void createSecondaryStack(String instanceId, List<String> ips, List<String> ports,
			Map<String, String> parametersSecondary, String templateSec, int i) {
		if (parametersSecondary.containsKey(ParameterManager.NETWORK_PORT)) {
			parametersSecondary.remove(ParameterManager.NETWORK_PORT);
		}
		parametersSecondary.put(ParameterManager.NETWORK_PORT, ports.get(i));

		if (parametersSecondary.containsKey(ParameterManager.SECONDARY_HOSTNAME)) {
			parametersSecondary.remove(ParameterManager.SECONDARY_HOSTNAME);
		}
		parametersSecondary.put(ParameterManager.SECONDARY_HOSTNAME, "sec-" + ips.get(i).replace(".", "-"));

		heatFluent.create("s" + instanceId + "_Sec" + i, templateSec, parametersSecondary, false, 10l);
	}

	private Stack createPrimaryStack(String instanceId, Map<String, String> customParameters) throws PlatformException {
		Map<String, String> parametersPrimary = ParameterManager.copyProperties(customParameters,
				ParameterManager.RABBIT_VHOST, ParameterManager.RABBIT_USER, ParameterManager.RABBIT_PASSWORD, LOG_HOST,
				LOG_PORT, ParameterManager.ERLANG_KEY, ParameterManager.FLAVOR, ParameterManager.VOLUME_SIZE,
				ParameterManager.ETC_HOSTS, ParameterManager.MASTER_HOSTNAME, ParameterManager.PORT_PRIM,
				AVAILABILITY_ZONE, KEYPAIR, IMAGE_ID);

		String templatePrimary = accessTemplate("/openstack/templatePrim.yaml");
		String namePrimary = "s" + instanceId + "_primary";

		heatFluent.create(namePrimary, templatePrimary, parametersPrimary, false, 10l);

		Stack primaryStack = stackProgressObserver.waitForStackCompletion(namePrimary);
		return primaryStack;
	}

	private Stack createPortsStack(String instanceId, Map<String, String> customParameters) throws PlatformException {
		Map<String, String> parametersPorts = ParameterManager.copyProperties(customParameters, NETWORK_ID,
				ParameterManager.PORT_NUMBER);

		String namePorts = "s" + instanceId + "_Ports";
		String templatePorts = accessTemplate("/openstack/templatePorts.yaml");

		heatFluent.create(namePorts, templatePorts, parametersPorts, false, 10l);

		Stack portsStack = stackProgressObserver.waitForStackCompletion(namePorts);
		return portsStack;
	}

	public String getLogPort() {
		return logPort;
	}

	public void setLogPort(String logPort) {
		this.logPort = logPort;
	}

	public String getLogHost() {
		return logHost;
	}

	public void setLogHost(String logHost) {
		this.logHost = logHost;
	}

	@Override
	protected Map<String, String> defaultParameters() {
		Map<String, String> defaultParameters = super.defaultParameters();
		defaultParameters.put(LOG_HOST, logHost);
		defaultParameters.put(LOG_PORT, logPort);
		return defaultParameters;
	}
}
