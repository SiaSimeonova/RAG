# Scheduled Maintenance

## Spring AI 2.0.0 GA upgrade

**Scheduled date:** 2026-05-28  
**Effort:** ~10 minutes  
**Risk:** Low — milestone → GA is a drop-in upgrade, no API changes expected

### What to do

1. Open `pom.xml`.

2. Change the Spring AI version:
   ```xml
   <!-- Before -->
   <spring-ai.version>2.0.0-M4</spring-ai.version>

   <!-- After -->
   <spring-ai.version>2.0.0</spring-ai.version>
   ```

3. Remove the Spring Milestones repository block (GA is on Maven Central):
   ```xml
   <!-- Remove this entire block -->
   <repositories>
       <repository>
           <id>spring-milestones</id>
           <name>Spring Milestones</name>
           <url>https://repo.spring.io/milestone</url>
           <snapshots>
               <enabled>false</enabled>
           </snapshots>
       </repository>
   </repositories>
   ```

4. Run the tests to verify nothing broke:
   ```bash
   mvn test -Dspring.profiles.active=test
   ```

5. Do a quick smoke test against the running application:
   - `GET /actuator/health` → should return `UP`
   - `POST /api/auth/login` → should return a token
   - `POST /api/rag/ask` → should return an answer

### Why this needs doing

The current version `2.0.0-M4` is a milestone release pulled from the Spring Milestones repository (`repo.spring.io/milestone`). Using milestone dependencies in production is not recommended — they may contain breaking changes between releases and are not covered by the standard Spring support policy. GA releases on Maven Central are stable, supported, and do not require the extra repository configuration.
