package com.uba.mbp.sharedplatform.notification;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * notification-service is consume-only in production (see KafkaConfig's own
 * javadoc) — it has no producer bean of its own. Tests still need to publish
 * onto the topics it consumes (owned by memo-balance/icad-integration-adapter
 * in production), so this test-only producer stands in for "some other
 * service published this event."
 */
@TestConfiguration
public class TestKafkaProducerConfig {

    @Bean
    public ProducerFactory<String, Object> testProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> testKafkaTemplate(ProducerFactory<String, Object> testProducerFactory) {
        return new KafkaTemplate<>(testProducerFactory);
    }
}
