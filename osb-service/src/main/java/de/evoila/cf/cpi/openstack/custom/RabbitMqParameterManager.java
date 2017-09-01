/**
 * 
 */
package de.evoila.cf.cpi.openstack.custom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.evoila.cf.broker.bean.RabbitMQCredentials;
import de.evoila.cf.broker.persistence.mongodb.repository.ClusterStackMapping;
import de.evoila.cf.cpi.openstack.custom.cluster.ClusterParameterManager;

public class RabbitMqParameterManager extends ClusterParameterManager {



	static void updatePortParameters(Map<String, String> customParameters, List<String> ips, List<String> ports) {
		String primaryIp = ips.remove(0);
		String primaryPort = ports.remove(0);
		
		customParameters.put(RabbitMqParameterManager.MASTER_IP, primaryIp);
		customParameters.put(RabbitMqParameterManager.MASTER_PORT, primaryPort);

		customParameters.put(RabbitMqParameterManager.MIRROR_IPS, String.join(",", ips));
		customParameters.put(RabbitMqParameterManager.MIRROR_PORTS, String.join(",", ports));
	}
	
	static void updateVolumeParameters(Map<String, String> customParameters, List<String> volumes) {
		String primaryVolume = volumes.remove(0);
		
		customParameters.put(RabbitMqParameterManager.MASTER_VOLUME_ID, primaryVolume);
		customParameters.put(RabbitMqParameterManager.MIRROR_VOLUME_IDS, String.join(",",volumes));
	}


}