package tracewise.retrieval;

/** A piece of text to index; source says where it came from (an indicator number or a case id). */
public record Passage(String source, String text) {
}
