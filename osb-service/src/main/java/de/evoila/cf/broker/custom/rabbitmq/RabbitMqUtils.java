package de.evoila.cf.broker.custom.rabbitmq;

public class RabbitMqUtils {

    public static String vHostName(String uuid) {
        if (uuid != null && uuid.length() > 15)
            return "v" + uuid.replace("-", "").substring(0, 15);
        else
            return null;
    }

}