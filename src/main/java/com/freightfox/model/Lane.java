package com.freightfox.model;

/**
 * A route between two locations that must be served by exactly one transporter.
 *
 * @param id          unique lane identifier
 * @param origin      origin location
 * @param destination destination location
 */
public record Lane(long id, String origin, String destination) {
}
