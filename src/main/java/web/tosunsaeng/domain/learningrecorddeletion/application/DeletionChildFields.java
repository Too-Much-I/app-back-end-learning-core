package web.tosunsaeng.domain.learningrecorddeletion.application;

/** Stored BSON field names for raw collection queries (no entity mapping is applied). */
public final class DeletionChildFields {
    private DeletionChildFields() {}

    public static String examReference(String collection) {
        return "azure_results".equals(collection) ? "exam_id" : "examId";
    }
}
