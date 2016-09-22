package de.aitools.ie.segmentation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.Tree;

/**
 * Segments a text based on clauses.
 * <p>
 * This segmentation algorithm starts a new segment at the beginning or end of
 * every clause not preceded by a relative pronoun. Clauses are identified using
 * the Stanford Parser (Manning et al., 2014) and the clause tags from the Penn
 * Treebank Guidelines (Bies et al., 1995).
 * </p><p>
 * Please cite the following paper when using this segmenter:
 * <pre>
 * Khalid Al-Khatib, Henning Wachsmuth, Johannes Kiesel, Matthias Hagen and Benno Stein
 * 2016
 * A News Editorial Corpus for Mining Argumentation Strategies
 * In Proceedings of the 26th International Conference on Computational Linguistics, COLING 2016
 * </pre>
 * </p><p>
 * To reproduce the values in Section 4.1, see {@link SegmentationEvaluator}.
 * </p><p>
 * References:
 * </p><p>
 * Ann Bies, Mark Ferguson, Karen Katz, Robert MacIntyre, Victoria Tredinnick, Grace Kim, Mary Ann Marcinkiewicz, and Britta Schasberger.
 * 1995.
 * Bracketing Guidelines for Treebank II Style Penn Treebank Project.
 * Technical report, University of Pennsylvania.
 * </p><p>
 * Christopher D. Manning, Mihai Surdeanu, John Bauer, Jenny Finkel, Steven J. Bethard, and David McClosky.
 * 2014.
 * The Stanford CoreNLP natural language processing toolkit.
 * In Association for Computational Linguistics (ACL) System Demonstrations,
 * pages 55-60.
 * </p>
 *
 * @author johannes.kiesel@uni-weimar.de
 *
 */
public class ClauseSegmenter {

  private static final Set<String> CLAUSE_INDICATORS =
      new TreeSet<>(Arrays.asList(new String[] {
          "s", "sbar", "sbarq", "sinv", "sq", "frag"
  }));

  private static final Set<String> RELATIVE_PRONOUNS =
      new TreeSet<>(Arrays.asList(new String[] {
          "who", "whom", "whose", "which", "that"
  }));

  private static final Set<String> NON_RELATIVE_PRONOUN_POS_TAG =
      new TreeSet<>(Arrays.asList(new String[] {
          "dt"
  }));
  
  private final StanfordParser stanfordParser;
  
  /**
   * Creates a new segmenter for texts of given locale.
   * <p>
   * Only {@link Locale#ENGLISH} has been used so far.
   * </p>
   * @param locale The locale to use
   */
  public ClauseSegmenter(final Locale locale) {
    this.stanfordParser = new StanfordParser(locale);
  }
  
  /**
   * Recursively segment all Files in the input (directory or file) and write
   * the segmented texts to output (directory or file).
   * <p>
   * Only files with extension <tt>.txt</tt> are segmented. 
   * </p><p>
   * If the input is a directory, a corresponding directory structure is created
   * in output.
   * </p><p>
   * The output files contain one segment per line. Depending on the value
   * printInterval, either only the segment text or also begin and end indices
   * are written.
   * </p>
   * @param input Input file or directory with <tt>txt</tt>-files 
   * @param output Output to write the segmented files to
   * @param printInterval Whether to write the segment interval
   * @throws IOException If an error occurred on reading or writing
   */
  public void segment(final File input, final File output,
      final boolean printInterval)
  throws IOException {
    if (input.isDirectory()) {
      output.mkdirs();
      for (final File inputChild : input.listFiles()) {
        if (inputChild.getName().endsWith(".txt")) {
          this.segmentFile(inputChild,
              new File(output, inputChild.getName()), printInterval);
        }
      }
    } else if (input.isFile()) {
      this.segmentFile(input, output, printInterval);
    }
  }
  
  private void segmentFile(final File input, final File output,
      final boolean printInterval)
  throws IOException {
    System.out.println("Segmenting: " + input + " -> " + output);
    final StringBuilder contentBuilder = new StringBuilder();
    try (final BufferedReader reader
        = new BufferedReader(new FileReader(input))) {
      String line = null;
      while ((line = reader.readLine()) != null) {
        contentBuilder.append(line).append('\n');
      }
    }
    
    try (final BufferedWriter writer
        = new BufferedWriter(new FileWriter(output))) {
      for (final Segment segment
          : this.segment(new Segment(contentBuilder.toString()))) {
        if (printInterval) {
          writer.write(segment.toString());
        } else {
          writer.write(segment.text);
        }
        writer.write('\n');
      }
    }
  }
  
  /**
   * Segments given text.
   * <p>
   * The text will first be broken down into paragraphs.
   * </p>
   * @param segment The text
   * @return The segments
   */
  public List<Segment> segment(final Segment segment) {
    final List<Segment> output = new ArrayList<>();
    final Pattern paragraphBreakPattern = Pattern.compile("\n\n");
    final Matcher matcher = paragraphBreakPattern.matcher(segment.text);
    
    int start = 0;
    while (matcher.find()) {
      final int end = matcher.start();
      final Segment paragraph =
          Segment.fromCompleteText(segment.text, start, end);
      output.addAll(this.segmentParagraph(paragraph));
      
      start = matcher.end();
    }
    if (start < segment.text.length()) {
      final Segment paragraph =
          Segment.fromCompleteText(segment.text, start, segment.text.length());
      output.addAll(this.segmentParagraph(paragraph));
    }
    
    return output;
  }
  
  /**
   * Segments given paragraph.
   * <p>
   * The paragraph will first be broken down into sentences.
   * </p>
   * @param segment The paragraph text
   * @return The segments
   */
  public List<Segment> segmentParagraph(final Segment segment) {
    final List<Segment> output = new ArrayList<>();
    for (final Segment sentence : this.stanfordParser.toSentences(segment)) {
      output.addAll(this.segmentSentence(sentence));
    }
    return output;
  }
  
  /**
   * Segments given sentence.
   * @param sentence The sentence text
   * @return The segments
   */
  public List<Segment> segmentSentence(final Segment sentence) {
    final List<Segment> output = new ArrayList<>();

    final List<Segment> words = this.stanfordParser.toWords(sentence);
    final boolean[] spanBoundaries =
        this.getSpanBoundaries(sentence.text, words);
    
    final int sentenceOffset = sentence.start;
    
    int segmentStart = sentence.start;
    for (int i = 0; i < spanBoundaries.length; ++i) {
      if (spanBoundaries[i]) {
        final int segmentEnd = words.get(i).end;
        output.add(Segment.fromSegment(sentence,
            segmentStart - sentenceOffset, segmentEnd - sentenceOffset, true));
        segmentStart = words.get(i + 1).start;
      }
    }
    output.add(Segment.fromSegment(sentence,
        segmentStart - sentenceOffset, sentence.text.length(), true));
    
    return output;
  }

  private boolean[] getSpanBoundaries(
      final String sentence, final List<Segment> words) {
    final boolean[] spanBoundaries = new boolean[words.size()];

    final Tree sentenceTree = this.stanfordParser.toTree(sentence);

    final Queue<Integer> clauseBoundaries =
        this.getClauseBoundaries(words.get(0).start, sentenceTree);

    int wordIndex = 1;
    while (wordIndex < words.size() && !clauseBoundaries.isEmpty()) {
      final int spanStart = words.get(wordIndex).start;
      if (spanStart < clauseBoundaries.peek()) {
        spanBoundaries[wordIndex] = false;
      } else {
        spanBoundaries[wordIndex - 1] = true;
        clauseBoundaries.poll();
        while (spanStart >= clauseBoundaries.peek()) {
          clauseBoundaries.poll();
        }
      }
      ++wordIndex;
    }
    
    return spanBoundaries;
  }

  private Queue<Integer> getClauseBoundaries(
      final int sentenceStart, final Tree sentenceTree) {
    final Queue<Integer> boundaries = new LinkedList<Integer>();

    final List<Tree> leaves = sentenceTree.getLeaves();
    final boolean[] clauseBoundaries =
        this.getTreeClauseBoundaries(sentenceTree, leaves);

    final ListIterator<Tree> leafIterator = leaves.listIterator();
    int clauseEnd = sentenceStart;
    for (int i = 0; i < clauseBoundaries.length; ++i) {
      final Tree leaf = leafIterator.next();
      final CoreLabel token = CoreLabel.class.cast(leaf.label());
      clauseEnd += token.before().length() + token.originalText().length();
      if (clauseBoundaries[i]) {
        boundaries.add(clauseEnd);
      }
    }

    return boundaries;
  }

  private boolean[] getTreeClauseBoundaries(
      final Tree sentenceTree, final List<Tree> leaves) {
    final boolean[] clauseBoundaries = new boolean[leaves.size()];
    clauseBoundaries[clauseBoundaries.length - 1] = true;

    final List<Tree> noRelativePronouns = new ArrayList<Tree>();

    for (Tree node : sentenceTree.preOrderNodeList()) {
      final String nodeValue = node.label().value().toLowerCase();
      if (node.getSpan() == null) {
        continue;
      }

      if (!node.isLeaf()
          && ClauseSegmenter.CLAUSE_INDICATORS.contains(nodeValue)) {
        ClauseSegmenter.setBoundaryAtStart(node, leaves, clauseBoundaries, true);
        ClauseSegmenter.setBoundaryAtEnd(node, leaves, clauseBoundaries, true);
      }

      if (ClauseSegmenter.NON_RELATIVE_PRONOUN_POS_TAG.contains(nodeValue)) {
        for (final Tree child : node.getChildrenAsList()) {
          noRelativePronouns.add(child);
        }
      }
    }

    A : for (final Tree leaf : leaves) {
      final String leafValue = leaf.label().value().toLowerCase();
      if (ClauseSegmenter.RELATIVE_PRONOUNS.contains(leafValue)) {
        for (final Tree noRelativePronoun : noRelativePronouns) {
          if (leaf == noRelativePronoun) {
            continue A;
          }
        }
        ClauseSegmenter.setBoundaryAtEnd(
            leaf.parent(sentenceTree), leaves, clauseBoundaries, false);
      }
    }
    return clauseBoundaries;
  }

  private static void setBoundaryAtStart(
      final Tree node,
      final List<Tree> leaves,
      final boolean[] clauseBoundaries,
      final boolean value) {
    final int start = node.getSpan().get(0);
    if (start > 0) {
      int begin = start;
      while (begin > 1) {
        final String previousLabel =
            leaves.get(begin - 1).label().value().toLowerCase();
        if (previousLabel.equals("``") || previousLabel.matches("-l.b-")) {
          --begin;
        } else {
          break;
        }
      }
      clauseBoundaries[begin - 1] = value;
    }
  }

  private static void setBoundaryAtEnd(
      final Tree node,
      final List<Tree> leaves,
      final boolean[] clauseBoundaries,
      final boolean value) {
    final int end = node.getSpan().get(1);
    clauseBoundaries[end] = value;
  }

  public static void main(final String[] args) throws IOException {
    boolean printInterval = true;
    
    if (args.length < 2 || args.length > 3) {
      System.err.println("Usage:");
      System.err.println("  <input> <output> [<print-interval>]");
      System.err.println("Synopsis:");
      System.err.println("  Segments the input based on clause indicators.");
      System.err.println("Parameters:");
      System.err.println("  <input>");
      System.err.println("    Input file or directory. In the latter case,");
      System.err.println("    the directory is traversed recursively and all");
      System.err.println("    txt-files are segmented.");
      System.err.println("  <output>");
      System.err.println("    Output file or directory. If the input is a");
      System.err.println("    directory, the output directories will be");
      System.err.println("    created to match the input directory structure.");
      System.err.println("  <print-interval>");
      System.err.println("    Whether to write the start and end character");
      System.err.println("    indices of each segment before the segment");
      System.err.println("    text. Default: " + printInterval);
      System.exit(0);
    }

    final File input = new File(args[0]);
    final File output = new File(args[1]);
    if (args.length > 2) { printInterval = Boolean.parseBoolean(args[2]); }

    final ClauseSegmenter segmenter = new ClauseSegmenter(Locale.ENGLISH);
    segmenter.segment(input, output, printInterval);
  }

}
