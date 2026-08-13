package com.donationmatch.matching.config;

import com.donationmatch.matching.event.DonationLotCreatedEvent;
import com.donationmatch.matching.event.RequestCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    private Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    @Bean
    public ConsumerFactory<String, DonationLotCreatedEvent> donationConsumerFactory() {
        JacksonJsonDeserializer<DonationLotCreatedEvent> deserializer =
                new JacksonJsonDeserializer<>(DonationLotCreatedEvent.class, false);

        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DonationLotCreatedEvent> donationListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, DonationLotCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(donationConsumerFactory());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, RequestCreatedEvent> requestConsumerFactory() {
        JacksonJsonDeserializer<RequestCreatedEvent> deserializer =
                new JacksonJsonDeserializer<>(RequestCreatedEvent.class, false);

        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RequestCreatedEvent> requestListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, RequestCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(requestConsumerFactory());
        return factory;
    }
}
