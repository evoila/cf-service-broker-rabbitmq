/**
 * 
 */
package de.evoila.cf.cpi.custom.props;

import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.keygen.BytesKeyGenerator;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.util.Base64Utils;

import de.evoila.cf.broker.model.Plan;
import de.evoila.cf.broker.model.ServiceInstance;

/**
 * @author Christian Brinker, evoila.
 *
 */
public class RabbitMQCustomPropertyHandler implements DomainBasedCustomPropertyHandler {
	
	private static final String RABBIT_PASSWORD = "rabbit_password";
	private static final String RABBIT_USER = "rabbit_user";
	private static final String RABBIT_VHOST = "rabbit_vhost";
	public static final String SECONDARY_NUMBER = "secondary_number";
	public static final String CLUSTER = "cluster";
	public static final String ERLANG_KEY = "erlang_key";
	
	private final Logger log = LoggerFactory.getLogger(RabbitMQCustomPropertyHandler.class);
	
	@Value("${rabbitmq.security.key.length}")
	private int keyLength;
		
	private BytesKeyGenerator secureRandom;
	
	@PostConstruct
	public void init() {
		secureRandom = KeyGenerators.secureRandom(keyLength);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.evoila.cf.cpi.openstack.custom.props.DomainBasedCustomPropertyHandler#
	 * addDomainBasedCustomProperties(de.evoila.cf.broker.model.Plan,
	 * java.util.Map, java.lang.String)
	 */
	@Override
	public Map<String, String> addDomainBasedCustomProperties(Plan plan, Map<String, String> customProperties,
			ServiceInstance serviceInstance) {
		String id = serviceInstance.getId();
		customProperties.put(RABBIT_VHOST, id);
		customProperties.put(RABBIT_USER, id);
		customProperties.put(RABBIT_PASSWORD, id);
		
		if(plan.getMetadata().containsKey(CLUSTER)) {
			Object uncastedCluster = plan.getMetadata().get(CLUSTER);
			if(uncastedCluster instanceof Boolean && (boolean) uncastedCluster) {
				customProperties.put(CLUSTER,"true");
				String key = Base64Utils.encodeToString(secureRandom.generateKey());
				customProperties.put(ERLANG_KEY, key);
				if (plan.getMetadata().containsKey(SECONDARY_NUMBER)) {
					String secNumber = plan.getMetadata().get(SECONDARY_NUMBER).toString();
					customProperties.put(SECONDARY_NUMBER, secNumber);
					log.debug("Count for cluster secondaries: "+secNumber);
				}
				log.debug("RabbitMQ cluster detected - add cluster to customProperties");				
			}
		}
				
		return customProperties;
	}
}
