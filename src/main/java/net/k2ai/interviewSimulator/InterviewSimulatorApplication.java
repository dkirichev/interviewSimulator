package net.k2ai.interviewSimulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InterviewSimulatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(InterviewSimulatorApplication.class, args);
	}// main

}// InterviewSimulatorApplication
