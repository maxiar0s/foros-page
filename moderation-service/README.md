# Moderation service

Standalone Java 17 / Spring Boot service on port 8083. Each moderation request requires `Authorization: Bearer <token>`; the service asks the users service `GET /api/auth/me` to resolve that token and checks the returned server-side role is `ADMIN`. Client-supplied role headers or body fields are ignored.

- `DELETE /api/moderation/topics/{topicId}` delegates to the existing forum API's `DELETE /api/topics/{topicId}`.
- `DELETE /api/moderation/topics/{topicId}/comments/{commentId}` delegates to `DELETE /api/topics/{topicId}/comments/{commentId}`.

The existing forum API implements removal rather than a reversible ban, so these moderation actions remove the targeted content using its existing contract and forward the same bearer token for its own authorization. Configure `USERS_SERVICE_URL` and `FORUM_SERVICE_URL` to override the default service URLs. Run `mvn test` and `mvn spring-boot:run` from this directory.
