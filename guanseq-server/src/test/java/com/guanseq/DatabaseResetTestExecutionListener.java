package com.guanseq;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

public final class DatabaseResetTestExecutionListener extends AbstractTestExecutionListener {

	private static final List<String> APPLICATION_SCHEMAS = List.of(
			"labeling",
			"equipment",
			"finance",
			"quality",
			"production",
			"procurement",
			"warehouse",
			"product",
			"planning",
			"sales",
			"masterdata",
			"identity",
			"platform",
			"public");

	@Override
	public int getOrder() {
		return 3500;
	}

	@Override
	public void beforeTestClass(TestContext testContext) throws Exception {
		if (!testContext.getTestClass().getSimpleName().endsWith("IntegrationTest")) {
			return;
		}

		var applicationContext = testContext.getApplicationContext();
		resetSchemas(applicationContext.getBean(DataSource.class));
		applicationContext.getBean(Flyway.class).migrate();
	}

	private void resetSchemas(DataSource dataSource) throws SQLException {
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			for (String schema : APPLICATION_SCHEMAS) {
				statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
			}
			statement.execute("CREATE SCHEMA public");
		}
	}
}
