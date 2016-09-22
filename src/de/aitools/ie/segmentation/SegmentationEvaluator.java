package de.aitools.ie.segmentation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates a segmentation by comparing it to ground-truth data.
 * <p>
 * The ground-truth has to be the following format, one segment per line:
 * <pre>
 * T&lt;segment-id&gt &lt;segment-start&gt &lt;segment-end&gt &lt;segment-text&gt
 * </pre>
 * Where segment-start and segment-end are the character indices of the first
 * and past the last character of the segment in the entire text respectively.
 * </p><p>
 * This program can be used to reproduce the values in Section 4.1 of
 * <pre>
 * Khalid Al-Khatib, Henning Wachsmuth, Johannes Kiesel, Matthias Hagen and Benno Stein
 * 2016
 * A News Editorial Corpus for Mining Argumentation Strategies
 * In Proceedings of the 26th International Conference on Computational Linguistics, COLING 2016
 * </pre>
 * To reproduce the results, you need the following directory structure:
 * <pre>
 * newspaper-corpus-segmenter.jar
 * lib/
 *   stanford-parser.jar
 *   stanford-parser-2.0.5-models.jar
 * essays/
 *   essays01.txt
 *   essays01.ann
 *   essays02.txt
 *   ...
 * </pre>
 * You can download the corpus from <a href="https://www.ukp.tu-darmstadt.de/data/argumentation-mining/argument-annotated-essays/">the UKP web page</a>.
 * To segment the corpus, run
 * <pre>
 * java -cp newspaper-corpus-segmenter.jar:lib/stanford-parser.jar:lib/stanford-parser-2.0.5-models.jar de.aitools.ie.segmentation.ClauseSegmenter essays segments
 * </pre>
 * To evaluate the segmentation, run
 * <pre>
 * java -cp newspaper-corpus-segmenter.jar:lib/stanford-parser.jar:lib/stanford-parser-2.0.5-models.jar de.aitools.ie.segmentation.SegmentationEvaluator segments essays
 * </pre>
 * </p>
 *
 * @author johannes.kiesel@uni-weimar.de
 *
 */
public class SegmentationEvaluator {
  
  private int countNoOverlaps;
  
  private int countSingleOverlaps;
  
  private int countMultiOverlaps;
  
  
  
  public void evaluateDirectories(
      final File segmentsDirectory, final File essaysGroundTruthDirectory)
  throws IOException {
    System.out.println("# Per essay, the number of automatically found");
    System.out.println("# segments that overlap with none, exactly one, or");
    System.out.println("# multiple ground-truth segments.");
    System.out.println("#essay #no-overlap #one-overlap #more-overlaps");
    
    this.countNoOverlaps = 0;
    this.countSingleOverlaps = 0;
    this.countMultiOverlaps = 0;
    for (final File segmentsFile : segmentsDirectory.listFiles()) {
      final File essaysGroundTruthFile = new File(essaysGroundTruthDirectory,
          segmentsFile.getName().replaceAll(".txt$", ".ann"));
      System.out.print(segmentsFile.getName() + "\t");
      this.evaluatePair(segmentsFile, essaysGroundTruthFile);
    }
    
    System.out.println("");
    System.out.println("# In total, the number of automatically found");
    System.out.println("# segments that overlap with none, exactly one, or");
    System.out.println("# multiple ground-truth segments.");
    System.out.println("#no-overlap #one-overlap #more-overlaps");
    System.out.println(this.countNoOverlaps + "\t"
        + this.countSingleOverlaps + "\t"
        + this.countMultiOverlaps);
    
    System.out.println("");
    System.out.println("# In total, the number of automatically found");
    System.out.println("# segments");
    System.out.println(this.countNoOverlaps + this.countSingleOverlaps
        + this.countMultiOverlaps);
  }
  
  public void evaluatePair(
      final File segmentsFile, final File essaysGroundTruthFile)
  throws IOException {
    int countNoOverlaps = 0;
    int countSingleOverlaps = 0;
    int countMultiOverlaps = 0;

    final List<Segment> groundTruthSegments =
        parseEssays(essaysGroundTruthFile);
    for (final Segment automaticSegment
        : Segment.parse(new FileInputStream(segmentsFile))) {
      int overlaps = 0;
      for (final Segment groundTruthSegment : groundTruthSegments) {
        if (doOverlap(automaticSegment, groundTruthSegment)) {
          ++overlaps;
        }
      }

      if (overlaps == 0) {
        ++countNoOverlaps;
      } else if (overlaps == 1) {
        ++countSingleOverlaps;
      } else {
        ++countMultiOverlaps;
      }
    }
    
    System.out.println(countNoOverlaps + "\t"
        + countSingleOverlaps + "\t"
        + countMultiOverlaps);
    
    this.countNoOverlaps += countNoOverlaps;
    this.countSingleOverlaps += countSingleOverlaps;
    this.countMultiOverlaps += countMultiOverlaps;
  }
  
  private static boolean doOverlap(
      final Segment segmentA, final Segment segmentB) {
    if (segmentA.start < segmentB.start) {
      return segmentB.start < segmentA.end;
    } else {
      return segmentA.start < segmentB.end;
    }
  }
  
  private static List<Segment> parseEssays(final File essaysGroundTruthFile)
  throws IOException {
    final List<Segment> segments = new ArrayList<>();
    try (final BufferedReader reader =
        new BufferedReader(new FileReader(essaysGroundTruthFile))) {
      String line = null;
      while ((line = reader.readLine()) != null) {
        if (!line.isEmpty()) {
          final String[] parts = line.split("\\s+", 5);
          final String id = parts[0];
          if (id.startsWith("T")) {
            final int start = Integer.parseInt(parts[2]);
            final int end = Integer.parseInt(parts[3]);
            final String text = parts[4];
            segments.add(new Segment(text, start, end));
          }
        }
      }
    }
    return segments;
  }
  
  public static void main(final String[] args) throws IOException {

    if (args.length != 2) {
      System.err.println("Usage:");
      System.err.println("  <segments> <essays>");
      System.err.println("Synopsis:");
      System.err.println("  Prints the number of automatically found segments");
      System.err.println("  that overlap with none, exactly one, or multiple");
      System.err.println("  ground-truth segments.");
      System.err.println("Parameters:");
      System.err.println("  <segments>");
      System.err.println("    Directory containing the segmented essays.");
      System.err.println("    Requires that the essays were segmented with");
      System.err.println("    print-interval bein true.");
      System.err.println("  <essays>");
      System.err.println("    Directory containing the ground-truth essay");
      System.err.println("    files.");
      System.exit(0);
    }
    
    final SegmentationEvaluator evaluator = new SegmentationEvaluator();
    evaluator.evaluateDirectories(new File(args[0]), new File(args[1]));
  }
  
}
