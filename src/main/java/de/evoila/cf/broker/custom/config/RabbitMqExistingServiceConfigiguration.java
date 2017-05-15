/**
 * 
 */
package de.evoila.cf.broker.custom.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import de.evoila.cf.broker.custom.RabbitMqExistingServiceFactory;

/**
 * @author Sebastian Boeing, evoila.
 *
 */

@Configuration
@EnableConfigurationProperties(value={RabbitMqExistingServiceFactory.class})
public class RabbitMqExistingServiceConfigiguration {

}


