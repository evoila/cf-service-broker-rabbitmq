/**
 * 
 */
package de.evoila.cf.cpi.openstack.custom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.evoila.cf.broker.persistence.mongodb.repository.ClusterStackMapping;
import de.evoila.cf.cpi.openstack.custom.cluster.ClusterParameterManager;

public class RabbitMqParameterManager extends ClusterParameterManager {



	static void updatePortParameters(Map<String, String> customParameters, List<String> ips, List<String> ports) {
		String primaryIp = ips.remove(0);
		String primaryPort = ports.remove(0);
		
		customParameters.put(RabbitMqParameterManager.MASTER_IP, primaryIp);
		customParameters.put(RabbitMqParameterManager.MASTER_PORT, primaryPort);
		
		String mirror1ip = ips.remove(0);
		String mirror1port = ports.remove(0);
		
		customParameters.put(RabbitMqParameterManager.MIRROR1_IP, mirror1ip);
		customParameters.put(RabbitMqParameterManager.MIRROR1_PORT, mirror1port);
		
		String mirror2ip = ips.remove(0);
		String mirror2port = ports.remove(0);
		
		customParameters.put(RabbitMqParameterManager.MIRROR2_IP, mirror2ip);
		customParameters.put(RabbitMqParameterManager.MIRROR2_PORT, mirror2port);
	}
	
	static void updateVolumeParameters(Map<String, String> customParameters, List<String> volumes) {
		String primaryVolume = volumes.remove(0);
		
		customParameters.put(RabbitMqParameterManager.MASTER_VOLUME_ID, primaryVolume);
		
		String mirror1Volume = volumes.remove(0);;
		
		customParameters.put(RabbitMqParameterManager.MIRROR1_VOLUME_ID, mirror1Volume);
		
		String mirror2Volume = volumes.remove(0);
		
		customParameters.put(RabbitMqParameterManager.MIRROR2_VOLUME_ID, mirror2Volume);
	}


}