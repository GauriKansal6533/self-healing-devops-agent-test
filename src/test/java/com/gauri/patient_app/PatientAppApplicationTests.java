package com.gauri.patient_app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatientAppApplicationTests {

	@Test
	void additionShouldWork() {

		PatientAppApplication app =
				new PatientAppApplication();// Phase 6 webhook test

		int result = app.add(2, 3);

		assertEquals(5, result);
	}
}