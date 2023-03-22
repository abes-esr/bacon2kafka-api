package fr.abes.kafkaconvergence.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicProducerError {

    @Value("${topic.errorname}")
    private String topicNameError;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void send(String key, String message){
        log.info("Message envoyé d'erreur: {}", message);
        kafkaTemplate.send(topicNameError, key, message);
    }

}
