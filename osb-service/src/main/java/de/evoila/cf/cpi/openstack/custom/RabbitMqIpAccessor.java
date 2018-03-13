/**
 * 
 */
package de.evoila.cf.cpi.openstack.custom;

import de.evoila.cf.broker.bean.OpenstackBean;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.ServerAddress;
import de.evoila.cf.broker.persistence.mongodb.repository.ClusterStackMapping;
import de.evoila.cf.broker.persistence.mongodb.repository.ClusterStackMappingRepository;
import de.evoila.cf.cpi.openstack.fluent.HeatFluent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Christian Brinker, evoila.
 *
 */
@Service
@Primary
@ConditionalOnBean(OpenstackBean.class)
public class RabbitMqIpAccessor extends CustomIpAccessor {

	@SuppressWarnings("unused")
	private HeatFluent heatFluent;

	@Autowired
	private ClusterStackMappingRepository stackMappingRepo;

	@Autowired
	private DefaultIpAccessor defaultIpAccessor;

	@Override
	public List<ServerAddress> getIpAddresses(String instanceId) throws PlatformException {
		ClusterStackMapping stackMapping = stackMappingRepo.findOne(instanceId);

		if (stackMapping != null) {
			return stackMapping.getServerAddresses();
		} else {
			return defaultIpAccessor.getIpAddresses(instanceId);
		}
	}

	@Autowired
	public void setHeatFluent(HeatFluent heatFluent) {
		this.heatFluent = heatFluent;
	}
}
