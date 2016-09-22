package de.aitools.ie.segmentation;

import java.io.ObjectInputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.WhitespaceTokenizer.WhitespaceTokenizerFactory;
import edu.stanford.nlp.process.WordToSentenceProcessor;
import edu.stanford.nlp.trees.Tree;

/**
 * Wrapper for the StanfordParser to provide simple access to the needed
 * functionalities.
 *
 * @author johannes.kiesel@uni-weimar.de
 *
 */
public class StanfordParser {

  private static final String PARSER_MODEL_PATH =
      "edu/stanford/nlp/models/lexparser/";

  private static final String[] PARSER_MODEL_VARIANTS = {
    "PCFG.ser.gz", "Factored.ser.gz"
  };

  private final Locale locale;

  private TokenizerFactory<CoreLabel> tokenizerFactory;

  private LexicalizedParser lexicalizedParser;

  /**
   * Creates a parser for given locale.
   * <p>
   * Will load the internal classes when they are required. This can then throw
   * an IllegalArgumentException when no parser model is found for given locale.
   * </p>
   * @param locale The locale of the texts to be processed
   * @throws NullPointerException If given locale is null
   */
  public StanfordParser(final Locale locale) throws NullPointerException {
    if (locale == null) { throw new NullPointerException(); }
    this.locale = locale;
    this.lexicalizedParser = null;
    this.tokenizerFactory = null;
  }

  /**
   * Segments given segment into sentences.
   * @param segment The input segment
   * @return A list of the sentences spanned by given segment
   */
  public List<Segment> toSentences(final Segment segment) {
    // Adapted from http://stackoverflow.com/a/30926661
    final List<CoreLabel> tokens = this.toTokens(segment.text);
    final WordToSentenceProcessor<CoreLabel> sentenceProcessor =
        new WordToSentenceProcessor<CoreLabel>();
    final List<List<CoreLabel>> sentenceTokens = 
        sentenceProcessor.process(tokens);

    int end;
    int start = 0;
    final List<Segment> sentences = new ArrayList<>();
    for (final List<CoreLabel> sentence: sentenceTokens) {
        end = sentence.get(sentence.size()-1).endPosition();
        sentences.add(Segment.fromSegment(segment, start, end, true));
        start = end;
    }
    return sentences;
  }

  /**
   * Tokenizes the provided text.
   * @param string The text
   * @return The tokens
   */
  public List<CoreLabel> toTokens(final String string) {
    final List<CoreLabel> tokens = new ArrayList<CoreLabel>();
    final Tokenizer<CoreLabel> tokenizer = this.getTokenizer(string);
    while (tokenizer.hasNext()) {
        tokens.add(tokenizer.next());
    }
    return tokens;
  }

  /**
   * Tokenizes the provided text, adding punctuation marks to the previous
   * token.
   * @param segment The text
   * @return The tokens
   */
  public List<Segment> toWords(final Segment segment) {
    final List<Segment> words = new ArrayList<Segment>();
    int start = 0;
    final ListIterator<CoreLabel> tokenIterator = 
        this.toTokens(segment.text).listIterator();
    while (tokenIterator.hasNext()) {
      CoreLabel token = tokenIterator.next();
      start += token.before().length();
      
      final StringBuilder wordTextBuilder = new StringBuilder();
      wordTextBuilder.append(token.originalText());
      while (token.after().isEmpty() && tokenIterator.hasNext()) {
        token = tokenIterator.next();
        if (StanfordParser.isCompleteWord(wordTextBuilder.toString())
            && StanfordParser.isWordStart(token)) {
          tokenIterator.previous(); // the first element of next segment
          tokenIterator.previous(); // the last element of current segment
          // and again the last element of current segment
          token = tokenIterator.next();
          break;
        } else {
          wordTextBuilder.append(token.before());
          wordTextBuilder.append(token.originalText());
        }
      }
      
      final int end = start + wordTextBuilder.length();
      words.add(Segment.fromSegment(segment, start, end, true));
      start = end;
    }

    return words;
  }

  /**
   * Parses the constituency tree from given sentence.
   * @param sentence The input sentence
   * @return The tree
   */
  public Tree toTree(final String sentence) {
    final Tokenizer<CoreLabel> tokenizer = this.getTokenizer(sentence);
    final Tree tree = this.getLexicalizedParser().apply(tokenizer.tokenize());
    tree.setSpans();
    return tree;
  }
  
  private static boolean isWordStart(final CoreLabel token) {
    final String value = token.value();
    if (Character.isLetterOrDigit(value.charAt(0))) {
      return true;
    } else if (value.matches("-L.B-")) {
      return true;
    }
    return false;
  }

  private static boolean isCompleteWord(final String word) {
    if (word.matches(".*[\\(\\[\\{]")) {
      return false;
    } else if (word.matches("[\"']")) {
      return false;
    }
    return true;
  }

  private LexicalizedParser getLexicalizedParser() {
    synchronized (this) {
      if (this.lexicalizedParser == null) {
        this.lexicalizedParser =
            StanfordParser.loadLexicalizedParser(this.locale);
      }
    }
    return this.lexicalizedParser;
  }

  private Tokenizer<CoreLabel> getTokenizer(final String string) {
    synchronized (this) {
      if (this.tokenizerFactory == null) {
        this.tokenizerFactory =
            StanfordParser.loadTokenizerFactory(this.locale);
      }
    }
    return this.tokenizerFactory.getTokenizer(new StringReader(string));
  }

  private static TokenizerFactory<CoreLabel> loadTokenizerFactory(
      final Locale locale) {
    if (locale.equals(Locale.ENGLISH)) {
      return PTBTokenizer.factory(
          new CoreLabelTokenFactory(), ",invertible=true");
    } else {
      return new WhitespaceTokenizerFactory<CoreLabel>(
          new CoreLabelTokenFactory(), ",invertible=true");
    }
  }

  private static LexicalizedParser loadLexicalizedParser(final Locale locale)
  throws IllegalArgumentException {
    for (final String path : StanfordParser.getLexicalizedParserPaths(locale)) {
      try {
        final ObjectInputStream parserStream =
            IOUtils.readStreamFromString(path);
        try {
          return LexicalizedParser.loadModel(parserStream);
        } finally {
          parserStream.close();
        }
      } catch (final Exception e) {
        // Path not found... still okay... try next
      }
    }
    throw new IllegalArgumentException("No model found for locale " + locale);
  }

  private static String[] getLexicalizedParserPaths(final Locale locale) {
    final int numVariants = StanfordParser.PARSER_MODEL_VARIANTS.length;
    final String[] paths = new String[numVariants];
    for (int v = 0; v < numVariants; ++v) {
      paths[v] = StanfordParser.PARSER_MODEL_PATH
          + locale.getDisplayName(Locale.ENGLISH).toLowerCase()
          + StanfordParser.PARSER_MODEL_VARIANTS[v];
    }
    return paths;
  }

}
