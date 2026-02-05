package net.k2ai.interviewSimulator.testutil;

import org.testcontainers.utility.DockerImageName;

public interface PostgresTestImages {

    DockerImageName POSTGRES_TEST_IMAGE = DockerImageName.parse("postgres:16-alpine");

    String POSTGRES_TEST_IMAGE_NAME = POSTGRES_TEST_IMAGE.asCanonicalNameString();

}//PostgresTestImages
