package edu.berkeley.cs.succinct.regex.executor;

import edu.berkeley.cs.succinct.regex.RegExMatch;
import edu.berkeley.cs.succinct.regex.parser.RegExParser;
import edu.berkeley.cs.succinct.regex.parser.RegExParsingException;

import java.util.Set;

public class SuccinctFwdRegExExecutorTest extends RegExExecutorTest {

  Set<RegExMatch> runRegEx(String exp, boolean greedy)
    throws RegExParsingException {
    RegExExecutor ex = new SuccinctFwdRegExExecutor(succinctFile, new RegExParser(exp).parse(), greedy);
    ex.execute();
    return ex.getFinalResults();
  }
}
