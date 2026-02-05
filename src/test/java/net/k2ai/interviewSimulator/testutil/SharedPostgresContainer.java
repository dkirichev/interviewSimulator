package net.k2ai.interviewSimulator.testutil;

import org.testcontainers.containers.PostgreSQLContainer;

public class SharedPostgresContainer extends PostgreSQLContainer<SharedPostgresContainer> {

    private static final String IMAGE_VERSION = PostgresTestImages.POSTGRES_TEST_IMAGE_NAME;

    private static SharedPostgresContainer container;


    @SuppressWarnings("resource")
    private SharedPostgresContainer() {
        super(IMAGE_VERSION);
        withCommand("postgres", "-c", "max_connections=300");
    }//SharedPostgresContainer


    public static SharedPostgresContainer getInstance() {
        if (container == null) {
            container = new SharedPostgresContainer();
            container.start();
        }
        return container;
    }//getInstance


    @Override
    public void stop() {
        // Do nothing, JVM handles shutdown
    }//stop

}//SharedPostgresContainer
