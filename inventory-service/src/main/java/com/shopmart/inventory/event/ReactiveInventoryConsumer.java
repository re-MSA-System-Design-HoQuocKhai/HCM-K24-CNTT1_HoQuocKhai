package com.shopmart.inventory.event;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Consumer Kafka viết theo kiểu reactive (WebFlux) để minh chứng tư duy Async / Event-driven.
 * Consumer này chạy song song với InventorySagaConsumer (consumer group khác)
 * và chỉ dùng để log/monitor các sự kiện.
 */
@Slf4j
@Component
public class ReactiveInventoryConsumer implements CommandLineRunner {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Override
    public void run(String... args) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "inventory-reactive-monitor");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderEvent.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        ReceiverOptions<String, OrderEvent> receiverOptions = ReceiverOptions
                .<String, OrderEvent>create(props)
                .subscription(Collections.singleton(KafkaTopics.ORDER));

        Flux<ReceiverRecord<String, OrderEvent>> kafkaFlux = KafkaReceiver.create(receiverOptions).receive();

        kafkaFlux
                .doOnNext(record -> {
                    OrderEvent event = record.value();
                    log.info("[REACTIVE-MONITOR] Event received: type={}, orderId={}, productId={}, qty={}, amount={}",
                            event.getType(), event.getOrderId(), event.getProductId(),
                            event.getQuantity(), event.getAmount());
                    record.receiverOffset().acknowledge();
                })
                .doOnError(error -> log.error("[REACTIVE-MONITOR] Error in reactive consumer: {}", error.getMessage()))
                .subscribe();

        log.info("[REACTIVE-MONITOR] Reactive Kafka consumer started on topic '{}'", KafkaTopics.ORDER);
    }
}
