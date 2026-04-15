package com.unios.service.marks;

import java.util.Map;

/**
 * Production-grade interface for pulling entrance exam marks.
 *
 * PRODUCTION INTEGRATION POINT:
 * When plugging into the real college exam system, create a class like
 * `CollegeSystemMarksPullService implements MarksPullService` that:
 * 1. Connects to the college's exam management software via REST API / JDBC
 * 2. Authenticates using the college's API key or DB credentials
 * 3. Fetches marks by hall ticket number / application ID
 * 4. Maps those marks to applicationId -> score
 *
 * For now, SimulatedMarksPullService is the active implementation.
 * To switch to production: swap the @Primary annotation from Simulated ->
 * Production.
 */
public interface MarksPullService {

    /**
     * Pulls exam scores for all scheduled students in a given batch.
     *
     * @param batchId The batch to fetch marks for
     * @return A map of applicationId -> score (0-100)
     */
    Map<Long, Double> pullMarksForBatch(Long batchId);
}
