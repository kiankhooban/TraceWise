package tracewise.domain;

/** Something the analyst should do on a case, for example "call the branch about C1". */
public record FollowUpTask(String description, boolean done) {
}
