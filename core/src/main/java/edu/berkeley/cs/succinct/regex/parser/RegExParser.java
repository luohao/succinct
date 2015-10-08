package edu.berkeley.cs.succinct.regex.parser;

public class RegExParser {

  private static final char WILDCARD = '@';
  private static final String RESERVED = "(){}[]|+*@";
  private static final RegEx BLANK = new RegExBlank();
  private String exp;

  /**
   * Constructor to initialize RegExParser from the input regular expression.
   * The supported grammar:
   * <regex> ::= <term> '|' <regex>
   * |  <term>
   * <term> ::= { <factor> }
   * <factor> ::= <base> { '*' | '+' | '{' <num> ',' <num> ')' }
   * <base> ::= <mgram>
   * |  '(' <regex> ')'
   * <mgram> ::= <char> | '\' <char> { <mgram> }
   * <num> ::= <digit> { <num> }
   *
   * @param exp The regular expression encoded as a UTF-8 String
   */
  public RegExParser(String exp) {
    // Replace all wildcard occurrences with single character
    exp = exp.replace(".*", String.valueOf(WILDCARD));

    // Remove leading or trailing wildcards, since they don't matter for us
    while (exp.charAt(0) == WILDCARD) {
      exp = exp.substring(1);
    }

    while (exp.charAt(exp.length() - 1) == WILDCARD) {
      exp = exp.substring(0, exp.length() - 1);
    }

    this.exp = exp;
  }

  /**
   * Parse the regular expression to create a regex tree
   * using a recursive descent parsing algorithm.
   *
   * @return The regular expression.
   * @throws RegExParsingException
   */
  public RegEx parse() throws RegExParsingException {
    return regex();
  }

  private char peek(int i) {
    return exp.charAt(i);
  }

  /**
   * Look at the next character to parse in the expression.
   *
   * @return The next character.
   */
  private char peek() {
    return exp.charAt(0);
  }

  /**
   * Eat the next character in the expression.
   *
   * @param c The expected character.
   * @throws RegExParsingException
   */
  private void eat(char c) throws RegExParsingException {
    if (peek() == c) {
      exp = exp.substring(1);
    } else {
      String message =
        "Could not parse regex expression; peek() = " + peek() + " trying to eat() = " + c;
      throw new RegExParsingException(message);
    }
  }

  /**
   * Get the next character in the expression, and eat it.
   *
   * @return The next character to be parsed.
   * @throws RegExParsingException
   */
  private char next() throws RegExParsingException {
    char c = peek();
    eat(c);
    return c;
  }

  /**
   * Get the next character in the expression, which is the part of a primitive, and eat it.
   *
   * @return The next primitive character.
   * @throws RegExParsingException
   */
  private char nextChar() throws RegExParsingException {
    if (peek() == '\\') {
      eat('\\');
    }
    return next();
  }

  /**
   * Get the next integer in the expression, and eat it.
   *
   * @return The next integer.
   * @throws RegExParsingException
   */
  private int nextInt() throws RegExParsingException {
    int num = 0;
    while (peek() >= 48 && peek() <= 57) {
      num = num * 10 + (next() - 48);
    }
    return num;
  }

  /**
   * Check if there are more characters to parse in the expression.
   *
   * @return Are there more characters to parse?
   */
  private boolean more() {
    return (exp.length() > 0);
  }

  /**
   * Expands character range, i.e., converts abbreviated ranges into full ranges.
   *
   * @param charRange Character range to be expanded.
   * @return The expanded character range.
   */
  private String expandCharRange(String charRange) {
    String expandedCharRange = "";
    for (int i = 0; i < charRange.length(); i++) {
      if (charRange.charAt(i) == '-') {
        char begChar = charRange.charAt(i - 1);
        char endChar = charRange.charAt(i + 1);
        for (char c = (char) (begChar + 1); c < endChar; c++) {
          expandedCharRange += c;
        }
        i++;
      }
      if (charRange.charAt(i) == '\\') {
        i++;
      }
      expandedCharRange += charRange.charAt(i);
    }
    return expandedCharRange;
  }

  /**
   * Top level method for recursive top-down parsing.
   * Parses the next regex (sub-expression).
   *
   * @return The parsed regex.
   * @throws RegExParsingException
   */
  private RegEx regex() throws RegExParsingException {
    RegEx t = term();
    if (more() && peek() == '|') {
      eat('|');
      RegEx r = regex();
      return new RegExUnion(t, r);
    }
    return t;
  }

  /**
   * Performs parse-level optimization for concatenation by consuming empty expressions and
   * merging chained multi-grams.
   *
   * @param a The left regular expression.
   * @param b The right regular expression.
   * @return The concatenated regular expression tree.
   */
  private RegEx concat(RegEx a, RegEx b) {
    if (a.getRegExType() == RegExType.Blank) {
      return b;
    } else if (a.getRegExType() == RegExType.Primitive && b.getRegExType() == RegExType.Primitive) {
      String aStr = ((RegExPrimitive) a).getMgram();
      String bStr = ((RegExPrimitive) b).getMgram();
      return new RegExPrimitive(aStr + bStr);
    }
    return new RegExConcat(a, b);
  }

  /**
   * Parses the next term.
   *
   * @return The parsed term.
   * @throws RegExParsingException
   */
  private RegEx term() throws RegExParsingException {
    RegEx f = BLANK;
    while (more() && peek() != ')' && peek() != '|') {
      if (peek() == WILDCARD) {
        eat(WILDCARD);
        RegEx nextF = factor();
        if (f.getRegExType() == RegExType.Blank || nextF.getRegExType() == RegExType.Blank) {
          throw new RegExParsingException("Invalid blank children of wildcard operator.");
        }
        f = new RegExWildcard(f, nextF);
      } else if (peek() == '[') {
        eat('[');
        String charRange = "";
        boolean repeat = false;
        while (peek() != ']') {
          charRange += next();
        }
        charRange = expandCharRange(charRange);
        eat(']');
        if (more() && (peek() == '+' || peek() == '*')) {
          next();
          repeat = true;
        }
        RegEx nextF = factor();
        f = new RegExCharRange(f, nextF, charRange, repeat);
      } else {
        RegEx nextF = factor();
        f = concat(f, nextF);
      }
    }
    return f;
  }

  /**
   * Parses the next factor.
   *
   * @return The parsed factor.
   * @throws RegExParsingException
   */
  private RegEx factor() throws RegExParsingException {
    RegEx b = base();

    if (more() && peek() == '*') {
      eat('*');
      b = new RegExRepeat(b, RegExRepeatType.ZeroOrMore);
    } else if (more() && peek() == '+') {
      eat('+');
      b = new RegExRepeat(b, RegExRepeatType.OneOrMore);
    } else if (more() && peek() == '{') {
      eat('{');
      int min = nextInt();
      eat(',');
      int max = nextInt();
      eat('}');
      b = new RegExRepeat(b, RegExRepeatType.MinToMax, min, max);
    }

    return b;
  }

  /**
   * Parses the next base.
   *
   * @return The parsed base.
   * @throws RegExParsingException
   */
  private RegEx base() throws RegExParsingException {
    if (more() && peek() == '(') {
      eat('(');
      RegEx r = regex();
      eat(')');
      return r;
    }
    return mgram();
  }

  /**
   * Parses the next multi-gram.
   *
   * @return The next multi-gram.
   * @throws RegExParsingException
   */
  private RegEx mgram() throws RegExParsingException {
    String m = "";
    while (more() && RESERVED.indexOf(peek()) == -1) {
      m += nextChar();
    }

    if (m == "") {
      return BLANK;
    }
    return new RegExPrimitive(m);
  }

}
