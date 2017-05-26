package de.evoila.cf.cpi.openstack.custom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.repository.MongoRepository;


/**
 * @author Christian Brinker, evoila.
 *
 */
@ConditionalOnProperty(prefix = "openstack", name = { "log_host", "log_port"}, havingValue = "")
public interface StackMappingRepository extends MongoRepository<RabbitMQStackMapping, String> {

}
