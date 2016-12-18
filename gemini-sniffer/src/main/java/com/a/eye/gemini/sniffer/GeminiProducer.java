package com.a.eye.gemini.sniffer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.stereotype.Component;

@Component
public class GeminiProducer {

	private static String kafkaproperty = "properties/kafka.properties";
	private static Properties properties = new Properties();
	private static KafkaProducer<Long, String> producer;

	static {
		InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(kafkaproperty);
		try {
			properties.load(inputStream);
		} catch (IOException e) {
			e.printStackTrace();
		}
		producer = new KafkaProducer<Long, String>(properties);
	}

	public KafkaProducer<Long, String> getProducer() {
		return producer;
	}
}