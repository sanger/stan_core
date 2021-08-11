package uk.ac.sanger.sccp.stan.service.work;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.stan.repo.CommentRepo;
import uk.ac.sanger.sccp.stan.repo.WorkEventRepo;

import java.util.Objects;

/**
 * Service for dealing with {@link WorkEvent work events}
 * @author dr6
 */
@Service
public class WorkEventServiceImp implements WorkEventService {
    private final WorkEventRepo workEventRepo;
    private final CommentRepo commentRepo;

    @Autowired
    public WorkEventServiceImp(WorkEventRepo workEventRepo, CommentRepo commentRepo) {
        this.workEventRepo = workEventRepo;
        this.commentRepo = commentRepo;
    }

    @Override
    public WorkEvent recordEvent(User user, Work work, WorkEvent.Type type, Comment comment) {
        WorkEvent event = new WorkEvent(work, type, user, comment);
        return workEventRepo.save(event);
    }

    /**
     * Gets the event type for putting a work in the given status
     * @param newStatus the new status
     * @return the event type that represents putting the work in the given status
     */
    public WorkEvent.Type findEventType(Status newStatus) {
        if (newStatus != null) {
            switch (newStatus) {
                case active: return WorkEvent.Type.resume;
                case paused: return WorkEvent.Type.pause;
                case failed: return WorkEvent.Type.fail;
                case completed: return WorkEvent.Type.complete;
            }
        }
        throw new IllegalArgumentException("Cannot determine event type for status "+newStatus);
    }

    @Override
    public WorkEvent recordStatusChange(User user, Work work, Status newStatus, Integer commentId) {
        Objects.requireNonNull(newStatus, "Cannot set work status to null.");
        Status oldStatus = work.getStatus();
        if (oldStatus == newStatus) {
            throw new IllegalArgumentException(String.format("Work %s status is already %s.", work.getWorkNumber(), newStatus));
        }
        if (work.isClosed()) {
            throw new IllegalArgumentException(String.format("Cannot alter status of %s work: %s", oldStatus, work.getWorkNumber()));
        }
        WorkEvent.Type eventType = findEventType(newStatus);
        boolean needReason = (eventType== WorkEvent.Type.pause || eventType== WorkEvent.Type.fail);
        if (needReason && commentId==null) {
            throw new IllegalArgumentException("A reason is required to "+eventType+" work.");
        }
        if (!needReason && commentId!=null) {
            throw new IllegalArgumentException("A reason is not required to "+eventType+" work.");
        }
        Comment comment = (commentId==null ? null : commentRepo.getById(commentId));
        return recordEvent(user, work, eventType, comment);
    }
}
