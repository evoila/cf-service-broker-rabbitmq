/**
 * 
 */
package de.evoila.cf.cpi.openstack.custom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParameterManager {

	public static final String RESOURCE_NAME = "resource_name";
	public static final String NODE_NUMBER = "node_number";
	public static final String IMAGE_ID = "image_id";
	public static final String KEY_NAME = "key_name";
	public static final String FLAVOR = "flavor";
	public static final String AVAILABILITY_ZONE = "availability_zone";
	public static final String VOLUME_SIZE = "volume_size";
	public static final String NETWORK_ID = "network_id";
	public static final String SECURITY_GROUPS = "security_groups";
	public static final String RABBITMQ_VHOST = "rabbitmq_vhost";
	public static final String ADMIN_USER = "admin_user";
	public static final String ADMIN_PASSWORD = "admin_password";
	public static final String ERLANG_KEY = "erlang_key";
	public static final String MASTER_VOLUME_ID = "master_volume_id";
	public static final String MASTER_PORT = "master_port";
	public static final String MASTER_IP = "master_ip";
	public static final String MIRROR1_VOLUME_ID = "mirror1_volume_id";
	public static final String MIRROR1_PORT = "mirror1_port";
	public static final String MIRROR1_IP = "mirror1_ip";
	public static final String MIRROR2_VOLUME_ID = "mirror2_volume_id";
	public static final String MIRROR2_PORT = "mirror2_port";
	public static final String MIRROR2_IP = "mirror2_ip";
	public static final String ETC_HOSTS = "etc_hosts";
	public static final String CLUSTER = "cluster";

	static void updatePortParameters(Map<String, String> customParameters, List<String> ips, List<String> ports) {
		String primaryIp = ips.remove(0);
		String primaryPort = ports.remove(0);
		
		customParameters.put(ParameterManager.MASTER_IP, primaryIp);
		customParameters.put(ParameterManager.MASTER_PORT, primaryPort);
		
		String mirror1ip = ips.remove(0);
		String mirror1port = ports.remove(0);
		
		customParameters.put(ParameterManager.MIRROR1_IP, mirror1ip);
		customParameters.put(ParameterManager.MIRROR1_PORT, mirror1port);
		
		String mirror2ip = ips.remove(0);
		String mirror2port = ports.remove(0);
		
		customParameters.put(ParameterManager.MIRROR2_IP, mirror2ip);
		customParameters.put(ParameterManager.MIRROR2_PORT, mirror2port);
	}
	
	static void updateVolumeParameters(Map<String, String> customParameters, List<String> volumes) {
		String primaryVolume = volumes.remove(0);
		
		customParameters.put(ParameterManager.MASTER_VOLUME_ID, primaryVolume);
		
		String mirror1Volume = volumes.remove(0);;
		
		customParameters.put(ParameterManager.MIRROR1_VOLUME_ID, mirror1Volume);
		
		String mirror2Volume = volumes.remove(0);
		
		customParameters.put(ParameterManager.MIRROR2_VOLUME_ID, mirror2Volume);
	}
	
	static int getSecondaryNumber(Map<String, String> customParameters) {
		return Integer.parseInt(customParameters.get(ParameterManager.NODE_NUMBER));
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