package root.cyb.mh.attendancesystem.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Configuration
public class DatabaseConstraintFixer {

    @Bean
    public CommandLineRunner fixPostgresConstraints(DataSource dataSource) {
        return args -> {
            try {
                JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
                // Drop the restrictive check constraint on the asset category column
                // Postgres creates this automatically for Enums, preventing new values
                String sql = "ALTER TABLE asset DROP CONSTRAINT IF EXISTS asset_category_check";
                jdbcTemplate.execute(sql);
                System.out.println(
                        "SUCCESS: Dropped legacy constraint 'asset_category_check' to allow new Asset Categories.");
            } catch (Exception e) {
                System.err.println(
                        "WARNING: Could not drop constraint 'asset_category_check'. This might be fine if it doesn't exist. Error: "
                                + e.getMessage());
            }
        };
    }
}
