package com.marketpulse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test") // in-memory database, never the local data/ file
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
