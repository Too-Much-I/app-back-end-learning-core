package web.tosunsaeng.domain.learningrecorddeletion.infrastructure;

import web.tosunsaeng.domain.learningrecorddeletion.domain.DeletionTarget;

public interface DeletionStorage {
    /** At most one bounded page. True means a fresh listing found no versions/objects. */
    boolean sweep(DeletionTarget target);
    boolean clearCache(DeletionTarget target);
}
