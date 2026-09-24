package ai.gargantua.trainer;

/** A single (text, skill name) training pair fed to the classifier trainer. */
public record TrainingExample(String skillName, String text) {}
