package ai.pipestream.registration.entity;

/**
 * Status of a registered service module
 */
public enum ServiceStatus {
    /** Service is active and operational */
    ACTIVE,
    /** Service is inactive or stopped */
    INACTIVE,
    /** Service is running but failing health checks */
    UNHEALTHY,
    /** Service is under maintenance */
    MAINTENANCE
}