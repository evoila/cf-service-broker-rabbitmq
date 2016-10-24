/**
 * 
 */
package de.evoila.cf.cpi.openstack.custom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParameterManager {
	private RabbitMQCustomStackHandler stackHandler;

	public static final String NETWORK_PORT = "network_port";
	public static final String PORT_PRIM = "port_prim";
	public static final String FLAVOR = "flavor";
	public static final String VOLUME_SIZE = "volume_size";
	public static final String SECONDARY_HOSTNAME = "secondaryHostname";
	public static final String MASTER_HOSTNAME = "masterHostname";
	public static final String ETC_HOSTS = "etcHosts";
	public static final String RABBIT_VHOST = "rabbit_vhost";
	public static final String RABBIT_USER = "rabbit_user";
	public static final String RABBIT_PASSWORD = "rabbit_password";
	public static final String ERLANG_KEY = "erlang_key";
	public static final String CLUSTER = "cluster";
	public static final String PORT_NUMBER = "port_number";
	public static final String SECONDARY_NUMBER = "secondary_number";

	/**
	 * @param rabbitMQCustomStackHandler
	 */
	public ParameterManager(RabbitMQCustomStackHandler stackHandler) {
		this.stackHandler = stackHandler;
	}

	Integer configureGeneralParameters(Map<String, String> customParameters) {
		customParameters.putAll(stackHandler.defaultParameters());

		customParameters.put(RabbitMQCustomStackHandler.LOG_PORT, stackHandler.getLogPort());
		customParameters.put(RabbitMQCustomStackHandler.LOG_HOST, stackHandler.getLogHost());

		Integer secondaryNumber = 0;

		if (customParameters.containsKey(ParameterManager.SECONDARY_NUMBER)) {
			secondaryNumber = Integer.parseInt(customParameters.get(ParameterManager.SECONDARY_NUMBER).toString());
			Integer portNumber = secondaryNumber + 1;
			customParameters.put(ParameterManager.PORT_NUMBER, portNumber.toString());
		}
		return secondaryNumber;
	}

	static void updateGeneralParameters(Map<String, String> customParameters, List<String> ips, List<String> ports) {
		String primIp = ips.get(0);
		ips.remove(0);
		String primPort = ports.get(0);
		customParameters.put(ParameterManager.PORT_PRIM, primPort);
		ports.remove(0);
		String primHostname = "p-" + primIp.replace(".", "-");

		String etcHosts = primIp + " " + primHostname + "\n";
		for (String secIp : ips) {
			etcHosts += secIp + " " + "sec-" + secIp.replace(".", "-") + "\n";
		}
		customParameters.put(ParameterManager.MASTER_HOSTNAME, primHostname);
		customParameters.put(ParameterManager.ETC_HOSTS, etcHosts);
	}

	static Map<String, String> copyProperties(Map<String, String> completeList, String... keys) {
		Map<String, String> copiedProps = new HashMap<>();

		for (int i = 0; i < keys.length; i++) {
			String key = keys[i];
			copiedProps.put(key, completeList.get(key));
		}
		return copiedProps;
	}
}