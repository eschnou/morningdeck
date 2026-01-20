# Troubleshooting

Common issues and their solutions for Morning Deck.

## Quick Diagnostics

### Health Check

```bash
# Overall health
curl http://localhost:3000/actuator/health

# Expected response
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "meilisearch": { "status": "UP" }
  }
}
```

### Database Connectivity

```bash
# Check PostgreSQL
psql -h localhost -U postgres -d morningdeck -c "SELECT 1"

# Check connection count
psql -c "SELECT count(*) FROM pg_stat_activity WHERE datname = 'morningdeck'"
```

### Queue Status

Check queue depths via logs or by adding a debug endpoint. High queue depths indicate processing backlog.

## Common Issues

### 1. Application Won't Start

#### Database Connection Failed

**Symptoms:**
```
Connection to localhost:5432 refused
```

**Solutions:**
1. Ensure PostgreSQL is running:
   ```bash
   docker-compose up -d db
   ```
2. Check connection settings in `application.properties`
3. Verify database exists:
   ```bash
   psql -h localhost -U postgres -c "SELECT datname FROM pg_database"
   ```

#### Port Already in Use

**Symptoms:**
```
Web server failed to start. Port 3000 was already in use.
```

**Solutions:**
1. Find and kill the process:
   ```bash
   lsof -i :3000
   kill -9 <PID>
   ```
2. Or change the port:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=3001"
   ```

#### Invalid JWT Secret

**Symptoms:**
```
JWT secret key must be at least 256 bits
```

**Solutions:**
Generate a proper secret:
```bash
openssl rand -base64 32
```

### 2. Authentication Issues

#### Token Expired

**Symptoms:**
- 401 responses with "Expired JWT token"
- User logged out unexpectedly

**Solutions:**
1. User re-authenticates (normal behavior)
2. Increase token expiration:
   ```properties
   spring.security.jwt.expiration=172800000  # 48 hours
   ```

#### Account Disabled

**Symptoms:**
- 403 response with "Account is disabled"

**Solutions:**
1. Check user status in database:
   ```sql
   SELECT email, enabled FROM users WHERE email = 'user@example.com';
   ```
2. Re-enable if appropriate:
   ```sql
   UPDATE users SET enabled = true WHERE email = 'user@example.com';
   ```

### 3. Source Fetching Issues

#### Sources Stuck in QUEUED/FETCHING

**Symptoms:**
- Sources show "QUEUED" or "FETCHING" status for > 10 minutes
- No new items appearing

**Diagnosis:**
```sql
SELECT id, name, fetch_status, updated_at
FROM source
WHERE fetch_status IN ('QUEUED', 'FETCHING')
AND updated_at < NOW() - INTERVAL '10 minutes';
```

**Solutions:**
1. Recovery job should auto-reset (runs every 5 minutes)
2. Manual reset:
   ```sql
   UPDATE source SET fetch_status = 'IDLE'
   WHERE fetch_status IN ('QUEUED', 'FETCHING')
   AND updated_at < NOW() - INTERVAL '10 minutes';
   ```

#### RSS Feed Errors

**Symptoms:**
- Source shows ERROR status
- No items from specific feed

**Diagnosis:**
1. Check source error message in database
2. Test feed URL directly:
   ```bash
   curl -I "https://example.com/feed.xml"
   ```

**Common causes:**
- Feed URL changed
- Feed requires authentication
- Rate limiting
- SSL certificate issues

### 4. AI Processing Issues

#### Items Stuck in PENDING/PROCESSING

**Symptoms:**
- News items not getting summaries/scores
- Items show status NEW, PENDING, or PROCESSING

**Diagnosis:**
```sql
SELECT status, COUNT(*)
FROM news_item
GROUP BY status;
```

**Solutions:**
1. Check OpenAI API key is valid
2. Check OpenAI quota/billing
3. Recovery job auto-resets stuck items
4. Manual reset:
   ```sql
   UPDATE news_item SET status = 'NEW'
   WHERE status IN ('PENDING', 'PROCESSING')
   AND updated_at < NOW() - INTERVAL '10 minutes';
   ```

#### OpenAI Rate Limiting

**Symptoms:**
- 429 errors in logs
- Slow processing

**Solutions:**
1. Reduce worker count:
   ```properties
   application.jobs.processing.worker-count=2
   ```
2. Reduce batch size:
   ```properties
   application.jobs.processing.batch-size=25
   ```

### 5. Briefing Issues

#### Briefings Not Executing

**Symptoms:**
- No daily reports generated
- Briefings stuck in ACTIVE status

**Diagnosis:**
```sql
SELECT id, name, status, scheduled_time, last_executed_at, timezone
FROM day_brief
WHERE status = 'ACTIVE';
```

**Common causes:**
1. Scheduled time not reached in user's timezone
2. No items with DONE status since last execution
3. User has no credits

**Solutions:**
1. Check timezone handling is correct
2. Verify items exist:
   ```sql
   SELECT COUNT(*) FROM news_item
   WHERE brief_id = '<brief-id>'
   AND status = 'DONE'
   AND created_at > '<last-executed-at>';
   ```

#### Empty Reports

**Symptoms:**
- Reports generated but contain no items

**Cause:**
No items with DONE status and minimum score threshold.

**Solution:**
Check scoring configuration and ensure AI processing is completing.

### 6. Search Issues

#### Meilisearch Not Working

**Symptoms:**
- Search returns no results
- Health check shows Meilisearch DOWN

**Diagnosis:**
```bash
# Check Meilisearch is running
curl http://localhost:7700/health

# Check index exists
curl http://localhost:7700/indexes/news_items
```

**Solutions:**
1. Start Meilisearch:
   ```bash
   docker-compose up -d meilisearch
   ```
2. Verify configuration:
   ```properties
   meilisearch.enabled=true
   meilisearch.host=http://localhost:7700
   ```
3. Reindex if needed (via admin endpoint or service call)

#### Search Results Out of Sync

**Symptoms:**
- Recently added items not appearing in search
- Deleted items still appearing

**Solutions:**
1. Check async indexing is working (check logs)
2. Trigger reindex for specific brief:
   ```java
   meilisearchSyncService.reindexBrief(briefId);
   ```
3. Full reindex:
   ```java
   meilisearchSyncService.reindexAll();
   ```

### 7. Email Issues

#### Emails Not Sending

**Symptoms:**
- No welcome/verification emails
- No report emails

**Diagnosis:**
1. Check email sender configuration
2. For `logs` sender, check console output
3. For `aws` sender, check SES dashboard

**Solutions:**
1. Verify configuration:
   ```properties
   application.email.sender=aws  # or smtp, logs
   application.email.from=verified@domain.com
   ```
2. For AWS SES:
   - Verify sender email/domain
   - Check sandbox mode (requires verified recipients)
   - Check sending quota

#### Inbound Emails Not Processing

**Symptoms:**
- Email source not receiving items

**Diagnosis:**
1. Check SQS queue for messages (if using SQS)
2. Check application logs for email processing
3. Verify source email address matches recipient

**Solutions:**
1. Verify SQS/SNS/SES rule configuration
2. Check S3 bucket permissions for raw email storage
3. Verify source is linked to correct email address

### 8. Credit Issues

#### User Out of Credits

**Symptoms:**
- Items not processing
- No new sources fetching
- Error message about credits

**Diagnosis:**
```sql
SELECT u.email, s.plan, s.credits_remaining
FROM users u
JOIN subscription s ON u.id = s.user_id
WHERE u.email = 'user@example.com';
```

**Solutions:**
1. User upgrades subscription plan
2. Admin adds credits manually:
   ```sql
   UPDATE subscription
   SET credits_remaining = credits_remaining + 1000
   WHERE user_id = (SELECT id FROM users WHERE email = 'user@example.com');
   ```

### 9. Performance Issues

#### Slow API Responses

**Diagnosis:**
1. Check database query performance
2. Check connection pool saturation
3. Check queue depths

**Solutions:**
1. Add database indexes if missing
2. Increase connection pool:
   ```properties
   spring.datasource.hikari.maximum-pool-size=20
   ```
3. Check for N+1 queries in logs

#### High Memory Usage

**Diagnosis:**
1. Check heap usage
2. Look for memory leaks in queues

**Solutions:**
1. Increase heap:
   ```bash
   java -Xmx2g -jar app.jar
   ```
2. Reduce queue capacities if memory-constrained

## Useful SQL Queries

### System Overview

```sql
-- User count by subscription plan
SELECT s.plan, COUNT(*) FROM subscription s GROUP BY s.plan;

-- Source count by type and status
SELECT type, fetch_status, COUNT(*)
FROM source
GROUP BY type, fetch_status;

-- News item count by status
SELECT status, COUNT(*)
FROM news_item
GROUP BY status;

-- Processing backlog
SELECT COUNT(*) AS pending_items
FROM news_item
WHERE status IN ('NEW', 'PENDING');
```

### Recent Activity

```sql
-- Recently processed items
SELECT id, title, status, updated_at
FROM news_item
ORDER BY updated_at DESC
LIMIT 10;

-- Recent errors
SELECT id, name, error_message, updated_at
FROM source
WHERE fetch_status = 'ERROR'
ORDER BY updated_at DESC
LIMIT 10;
```

## Log Analysis

### Key Log Patterns

```bash
# Processing errors
grep "ProcessingWorker.*ERROR" app.log

# Fetch errors
grep "FetchWorker.*ERROR" app.log

# Authentication failures
grep "JwtAuthenticationFilter.*401" app.log

# Credit deductions
grep "deductCredit" app.log
```

### Request Tracing

Each request has a unique ID in MDC. Find related logs:
```bash
grep "request-id-here" app.log
```

## Getting Help

If issues persist:

1. Check application logs with DEBUG level:
   ```properties
   logging.level.be.transcode.morningdeck=DEBUG
   ```

2. Check database state with queries above

3. Verify all external services are accessible:
   - PostgreSQL
   - Meilisearch (if enabled)
   - OpenAI API
   - AWS services (if used)

## Related Documentation

- [Configuration](./configuration.md) - Configuration options
- [Deployment](./deployment.md) - Deployment guide
- [Queue System](../architecture/queue-system.md) - Queue architecture
