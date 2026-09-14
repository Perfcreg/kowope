package com.uba.mbp.sharedplatform.notification.config;

import com.uba.mbp.sharedplatform.notification.event.IcadClearanceOutcomeEvent;
import com.uba.mbp.sharedplatform.notification.event.MemoLiquidatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Explicit Kafka consumer wiring (ADR-0001), mirroring memo-balance's
 * KafkaConfig exactly — this service only consumes (the two real triggers
 * described in ADR-0019), it never publishes, so there's no producer/
 * KafkaTemplate bean here.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    private static final String GROUP_ID = "notification-service";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, MemoLiquidatedEvent> memoLiquidatedConsumerFactory() {
        Map<String, Object> props = baseConsumerProps();
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.uba.mbp.sharedplatform.notification.event");
        props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, MemoLiquidatedEvent.class.getName());
        props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MemoLiquidatedEvent> memoLiquidatedContainerFactory(
            ConsumerFactory<String, MemoLiquidatedEvent> memoLiquidatedConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, MemoLiquidatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(memoLiquidatedConsumerFactory);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, IcadClearanceOutcomeEvent> icadClearanceOutcomeConsumerFactory() {
        Map<String, Object> props = baseConsumerProps();
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.uba.mbp.sharedplatform.notification.event");
        props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, IcadClearanceOutcomeEvent.class.getName());
        props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, IcadClearanceOutcomeEvent> icadClearanceOutcomeContainerFactory(
            ConsumerFactory<String, IcadClearanceOutcomeEvent> icadClearanceOutcomeConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, IcadClearanceOutcomeEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(icadClearanceOutcomeConsumerFactory);
        return factory;
    }

    private Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
        return props;
    }
}
