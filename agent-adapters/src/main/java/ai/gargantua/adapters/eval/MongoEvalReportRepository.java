package ai.gargantua.adapters.eval;

import ai.gargantua.core.eval.EvalReport;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for persisting and querying eval reports.
 * Stores documents in the {@code eval_reports} collection, supporting
 * retrieval of the latest report per skill for regression comparison.
 */
@Component
public class MongoEvalReportRepository {

    private static final String COLLECTION = "eval_reports";

    private final MongoTemplate mongoTemplate;

    public MongoEvalReportRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void save(EvalReport report) {
        mongoTemplate.insert(report, COLLECTION);
    }

    public Optional<EvalReport> findLatest(String skillName) {
        Query query = new Query(Criteria.where("skillName").is(skillName))
                .with(Sort.by(Sort.Direction.DESC, "runAt"))
                .limit(1);
        EvalReport result = mongoTemplate.findOne(query, EvalReport.class, COLLECTION);
        return Optional.ofNullable(result);
    }

    public List<EvalReport> findHistory(String skillName, int limit) {
        Query query = new Query(Criteria.where("skillName").is(skillName))
                .with(Sort.by(Sort.Direction.DESC, "runAt"))
                .limit(limit);
        return mongoTemplate.find(query, EvalReport.class, COLLECTION);
    }
}
