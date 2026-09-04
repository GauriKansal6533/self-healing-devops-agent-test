package com.gauri.patient_app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PatientAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientAppApplication.class, args);
    }

    public int add(int a, int b) {
        return 1; // Trigger self-healing pipeline - test 4
    }
}