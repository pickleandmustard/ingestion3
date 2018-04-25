package dpla.ingestion3.enrichments.normalizations

import org.apache.commons.lang.StringEscapeUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document.OutputSettings
import org.jsoup.nodes.Entities.EscapeMode
import org.jsoup.safety.Whitelist

import scala.util.matching.Regex

/**
  * String enrichments
  *
  */
object StringNormalizationUtils {

  implicit class Normalizations(value: String) {

    type SingleStringEnrichment = String
    type MultiStringEnrichment = Array[String]

    /**
      * Accepts a set of Strings to preserve in the original value. Those string values
      * can be literal strings or regular expressions. Regular expressions can allow for
      * filtering out terms while:
      *   - ignoring white space
      *   - ignoring case
      *   - ignoring punctuation
      *   - Greedy matching on plurals
      *
      * Construction and testing of those regular expressions is dependent on the user
      *
      */
    lazy val applyAllowFilter: Set[String] => String = (allowList) => {
      val nonAllowedTerms =
        // Remove terms in allowList from the original string
        applyBlockFilter(allowList)
        // Split the remaining terms (e.g. the terms to remove) on white space
        // This allows for filtering of specific words from a string but that functionality
        // is not yet a requirement of this filter.
        // FIXME This assumption may not hold in all cases and they should be clearly documented
        .split(" ")
        // Create a new set of regular expressions from the non-allowed terms
        // with a different base pattern
        .map(FilterRegex.Regex(_).allowListRegex)
        .toSet

      // Applies the block filter using nonAllowedTerms created from the original
      // string to the original string. Trims leading/trailing whitespace and
      // reduces extra interior whitespace
      applyBlockFilter(nonAllowedTerms).reduceWhitespace
    }
    /**
      * Applies the provided set of patterns and removes all matches from the original string
      */
    lazy val applyBlockFilter: Set[String] => String = (termList) => termList
      .foldLeft(value) {
        case (string, pattern) => string.replaceAll(pattern, "").reduceWhitespace
      }

    /**
      * Find and capitalize the first character in a given string
      *
      * 3 blind mice -> 3 blind mice
      * three blind mice -> Three blind mice.
      * ...vacationland... -> ...Vacationland...
      *   The first bike ->   The first bike
      *
      * @return
      */
    lazy val capitalizeFirstChar: SingleStringEnrichment = {
      val charIndex = findFirstChar(value)
      if (charIndex >= 0)
        replaceCharAt(value, charIndex, value.charAt(charIndex).toUpper)
      else
        value
    }

    /**
      * Removes trailing colons, semi-colons, commas, slashes, hyphens and whitespace
      * characters (whitespace, tab, new line and line feed) that follow the last letter
      * or digit
      *
      */
    lazy val cleanupEndingPunctuation: SingleStringEnrichment = {
      // FIXME rewrite as a regular expression
      val endIndex = value.lastIndexWhere(_.isLetterOrDigit)
      if (endIndex == -1)
      // If there is nothing to cleanup then return the existing string
        value
      else {
        val start = value.substring(0, endIndex + 1)
        val end = value.substring(endIndex+1)
        val cleanEnd = end.replaceAll(beginAndEndPunctuationToRemove,"")
        start.concat(cleanEnd)
      }
    }

    /**
      * Removes leading colons, semi-colons, commas, slashes, hyphens and whitespace
      * characters (whitespace, tab, new line and line feed) that precede the first letter
      * or digit
      *
      */
    lazy val cleanupLeadingPunctuation: SingleStringEnrichment = {
      val beginIndex = value.indexWhere(_.isLetterOrDigit)
      if (beginIndex == -1)
        value
      else
        value
          .substring(0, beginIndex)
          .replaceAll(beginAndEndPunctuationToRemove, "")
          .concat(value.substring(beginIndex))
    }

    /**
      * Accepts a String value and splits it around periods. Strips
      * trailing and leading whitespace from those "sentences" and
      * capitalizes the first character of each sentence leaving all
      * other characters alone.
      *
      * We do not assume that all upper case characters should be
      * down-cased.
      *
      */
    lazy val convertToSentenceCase: SingleStringEnrichment = {
      val pattern: Regex = """.*?(\.)""".r
      val sentences = for( t <- pattern findAllIn value) yield t.trim.capitalize
      // rejoin the sentences and add back the whitespace that was trimmed off
      sentences.mkString(" ")
    }

    /**
      * Truncates the string at the specified length
      */
    lazy val limitCharacters: (Int) => (String) = (length) => {
      if (value.length > length) value.substring(0, length)
      else value
    }

    /**
      * Reduce multiple whitespace values to a single and removes
      * leading and trailing white space
      */
    lazy val reduceWhitespace: SingleStringEnrichment = value.replaceAll(" +", " ").trim


    /**
      * Splits a String value around a given delimiter.
      */
    lazy val splitAtDelimiter: (String) => Array[String] = (delimiter) => {
      value.split(delimiter).map(_.trim).filter(_.nonEmpty)
    }

    /**
      * Bracket removal normalization
      *
      * Removes matching leading and trailing brackets (square, round and curly braces)
      * from a string
      */
    lazy val stripBrackets: SingleStringEnrichment = {
      val replacementRegex = for( (p, r) <- bracketPatterns if value.matches(p)) yield r
      value.replaceAll(replacementRegex.mkString, "")
    }

    /**
      * Strip all double quotes from the given string
      */
    lazy val stripDblQuotes: SingleStringEnrichment = value.replaceAll("\"", "")

    /**
      *
      */
    lazy val stripHTML: SingleStringEnrichment = {
      val unescaped = StringEscapeUtils.unescapeHtml(value)
      val cleaned = Jsoup.clean(unescaped, "", Whitelist.none(), new OutputSettings().escapeMode(EscapeMode.xhtml))
      StringEscapeUtils.unescapeHtml(cleaned)
    }

    /**
      * Removes singular period from the end of a string. Ignores and removes trailing whitespace
      */
    lazy val stripEndingPeriod: SingleStringEnrichment = {
      if (value.matches(""".*?[^\.]\.[\n\r\s]*$"""))
        value.replaceAll("""\.[\n\r\s]*$""", "")
      else
        value
    }

    /**
      * Punctuation and whitespace characters that should be removed
      * from leading and trailing parts of string
      *
      *   semicolon (;)
      *   colon (:)
      *   slash (/)
      *   comma (,)
      *   hyphen (-)
      *   new line (\n)
      *   tab (\t)
      *   carriage return (\r)
      *   whitespace (\s)
      */
    private def beginAndEndPunctuationToRemove = """[-;:,\/\\t\\r\\n\s]"""

    /**
      * Iterates over the string to find the first alphanumeric char
      * and returns the index
      *
      * @param str String to search
      * @return
      */
    private def findFirstChar(str: String): Int = {
      str.indexWhere(_.isLetterOrDigit)
    }

    /**
      * Replaces the character at the specified index in s with c
      * @param s String value
      * @param pos Index
      * @param c New char
      * @return A new String with Char c at index pos
      */
    private def replaceCharAt(s: String, pos: Int, c: Char): String = s.substring(0, pos) + c + s.substring(pos + 1)

    /**
      * Bracket patterns to search for and the corresponding replacement regex
      *
      * E.x.
      *   pattern to search for -> pattern to replace matches
      *   if it starts with ( and ends with ) -> get the first ( and last )
      *   """^(\(.*\)$)""" -> """^(\()|(\))$"""
      *
      *
      * @return Map[String, String]
      */
    private def bracketPatterns = {
      brackets.map(bracket => baseBracketSearchPattern(bracket) -> baseBracketReplacePattern(bracket))
    }

    /**
      * Constructs the regex pattern to search a string
      *
      * @param bracket Bracket pairs to search for
      * @return String Regex
      */
    def baseBracketSearchPattern(bracket: Bracket): String =
      """^([\n\r\s]*\""" + bracket.openChar + """.*\""" + bracket.closeChar + """[\n\r\s]*)"""

    /**
      * Constructs the regex pattern to perform a replacement
      *
      * @param bracket Bracket pairs to search for
      * @return String Regex
      */
    private def baseBracketReplacePattern(bracket: Bracket) =
      """^([\n\r\s]*\""" + bracket.openChar + """[\n\r\s]*)|([\n\r\s]*\""" + bracket.closeChar + """[\n\r\s]*)$"""

    private def brackets = Seq(
      Bracket("{","}"),
      Bracket("(",")"),
      Bracket("[","]")
    )

    /**
      * Case class that represents a pair of enclosing strings that
      * @param openChar String that indicates start of enclosure
      * @param closeChar String that indicates end of enclosure
      */
    case class Bracket(openChar: String, closeChar: String)
  }
}
