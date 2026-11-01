package com.clutterseller;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApplicationIntegrationTest {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private Flyway flyway;

	@Test
	void healthEndpointReportsUp() {
		assertThat(mvc.get().uri("/actuator/health"))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.status")
				.isEqualTo("UP");
	}

	@Test
	void flywayAppliesBaselineMigration() {
		assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
	}

}
