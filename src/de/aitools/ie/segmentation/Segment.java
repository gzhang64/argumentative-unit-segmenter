package de.aitools.ie.segmentation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Structure that holds one piece of a text including its position in the whole
 * text.
 *
 * @author johannes.kiesel@uni-weimar.de
 *
 */
public class Segment {
  
  /**
   * The text of this segment.
   */
  public final String text;

  /**
   * Character index at which the text of this segment starts in the whole text.
   */
  public final int start;
  
  /**
   * Character index after the last character of the text of this segment in the
   * whole text.
   */
  public final int end;
  
  /**
   * Creates a new segment that spans the entire text
   */
  public Segment(final String text) throws NullPointerException {
    this(text, 0, text.length());
  }
  
  /**
   * Creates a new segment that is part of a larger text.
   * @param text The text of only this segment
   * @param start The index of the first character of this segment in the
   * complete text
   * @param end The index past the last character of this segment in the
   * complete text
   */
  public Segment(final String text, final int start, final int end) {
    this.text = text;
    this.start = start;
    this.end = end;
  }
  
  /**
   * Creates a new segment that is part of given text.
   * @param completeText The text
   * @param start The index of the first character of this segment in the
   * complete text
   * @param end The index past the last character of this segment in the
   * complete text
   * @return The segment
   */
  public static Segment fromCompleteText(
      final String completeText, final int start, final int end)
  throws NullPointerException, IndexOutOfBoundsException {
    return new Segment(completeText.substring(start, end), start, end);
  }
  
  /**
   * Creates a new segment that is part of given segment
   * @param parent The segment of which the new segment should be a sub-segment
   * @param start The index of the first character of this segment in the
   * parent text
   * @param end The index past the last character of this segment in the
   * parent text
   * @param trim If to trim the new segment of white spaces at its borders
   * @return The new segment
   */
  public static Segment fromSegment(
      final Segment parent, int start, int end, final boolean trim)
  throws NullPointerException, IllegalArgumentException {
    if (trim) {
      start = Segment.trimStart(parent, start);
      end = Segment.trimEnd(parent, end);
    }
    return new Segment(parent.text.substring(start, end),
        parent.start + start, parent.start + end);
  }
  
  @Override
  public String toString() {
    return this.start + "\t" + this.end + "\t" + this.text.replace('\n', ' ');
  }
  
  /**
   * Parses segments that were serialized using {@link #toString()} with one
   * segment per line.
   * <p>
   * Not that line breaks within the segment are lost during serialization and
   * replaced by whitespace. So serializing and deserializing can lead to a
   * different segment text.
   * </p><p>
   * When the input does not contain serialized segments, either an
   * IndexArrayOutOfBoundsException or a NumberFormatException are thrown.
   * </p>
   * @param input Stream of segments to read from
   * @return Parsed list of segments
   * @throws IOException When an error occurred on reading from given input
   */
  public static List<Segment> parse(final InputStream input)
  throws IOException {
    final List<Segment> segments = new ArrayList<>();
    try (final BufferedReader reader =
        new BufferedReader(new InputStreamReader(input))) {
      String line = null;
      while ((line = reader.readLine()) != null) {
        if (!line.isEmpty()) {
          final String[] parts = line.split("\t", 3);
          final int start = Integer.parseInt(parts[0]);
          final int end = Integer.parseInt(parts[1]);
          final String text = parts[2];
          segments.add(new Segment(text, start, end));
        }
      }
    }
    return segments;
  }
  
  private static int trimStart(final Segment parent, int start) {
    while (start < parent.text.length()
        && Character.isWhitespace(parent.text.charAt(start))) {
      ++start;
    }
    return start;
  }

  private static int trimEnd(final Segment parent, int end) {
    while (end > 0
        && Character.isWhitespace(parent.text.charAt(end - 1))) {
      --end;
    }
    return end;
  }
}
