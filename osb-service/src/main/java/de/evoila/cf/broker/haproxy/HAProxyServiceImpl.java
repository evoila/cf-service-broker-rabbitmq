/**
 * 
 */
package de.evoila.cf.broker.haproxy;

import de.evoila.cf.broker.bean.HAProxyConfiguration;
import de.evoila.cf.broker.model.Mode;
import de.evoila.cf.broker.model.catalog.ServerAddress;
import de.evoila.cf.broker.service.HAProxyService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Rene Schollmeyer
 *
 */

@Service
public class HAProxyServiceImpl extends HAProxyService {

	public HAProxyServiceImpl(HAProxyConfiguration haProxyConfiguration) {
		super(haProxyConfiguration);
	}

	@Override
	public Mode getMode(ServerAddress serverAddress) {
		if(serverAddress.getPort() == 15672)
			return Mode.HTTP;
		return Mode.TCP;
	}
	
	@Override
	public List<String> getOptions(ServerAddress serverAddress) {
		return new ArrayList<String>();
	}
}
