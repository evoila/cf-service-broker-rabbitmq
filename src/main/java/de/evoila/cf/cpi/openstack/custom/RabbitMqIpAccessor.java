/**
 * 
 */
package de.evoila.cf.cpi.openstack.custom;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.ServerAddress;
import de.evoila.cf.cpi.openstack.fluent.HeatFluent;

/**
 * @author Christian Brinker, evoila.
 *
 */
@Service
@Primary
public class RabbitMqIpAccessor extends CustomIpAccessor {

	private HeatFluent heatFluent;

	@Autowired
	private StackMappingRepository stackMappingRepo;

	@Autowired
	private DefaultIpAccessor defaultIpAccessor;

	@Override
	public List<ServerAddress> getIpAddresses(String instanceId) throws PlatformException {
		StackMapping stackMapping = stackMappingRepo.findOne(instanceId);

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
