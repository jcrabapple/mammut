package org.joinmastodon.android.ui.utils;

import android.content.Context;

import org.joinmastodon.android.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Helpers for the search operator chips (G11). Everything is derived from the query text;
 * nothing is stored. The query is tokenized once into terms that know whether they are
 * quoted and whether they carry a - or + prefix; chip state, validity and token removal
 * all read that one term list, so a token inside a quoted phrase is phrase text and is
 * never treated as an operator (§5, §8). The validity rule mirrors Mastodon's own
 * SearchQueryTransformer: a query made only of operators is rejected ("At least one
 * keyword or phrase is required") unless it scopes to the library (in:library) or to
 * the user's own posts (from:me / from:<self>).
 */
public class SearchQuerySupport{
	// app/lib/search_query_transformer.rb — SUPPORTED_PREFIXES
	private static final List<String> SUPPORTED_PREFIXES=Arrays.asList("has", "is", "language", "from", "before", "after", "during", "in");

	public static final class ChipSpec{
		public final int labelRes;
		public final String token;

		private ChipSpec(int labelRes, String token){
			this.labelRes=labelRes;
			this.token=token;
		}
	}

	// Order is the spec (§4) order.
	public static final ChipSpec[] CHIPS={
			new ChipSpec(R.string.search_chip_media, "has:media"),
			new ChipSpec(R.string.search_chip_links, "has:link"),
			new ChipSpec(R.string.search_chip_polls, "has:poll"),
			new ChipSpec(R.string.search_chip_replies, "is:reply"),
			new ChipSpec(R.string.search_chip_sensitive, "is:sensitive"),
			new ChipSpec(R.string.search_chip_my_posts, "from:me"),
			new ChipSpec(R.string.search_chip_my_library, "in:library"),
	};

	/**
	 * One term of the query: a whitespace-delimited word, or the content of a quoted
	 * region, with any - / + prefix recorded separately. Mirrors what the server's
	 * SearchQueryParser produces: term characters are anything but whitespace, quote
	 * and colon, a phrase is quote-delimited, and a lone unterminated quote parses as
	 * junk while the remaining text still searches.
	 */
	public static final class Term{
		public final String text; // without the - / + prefix and without surrounding quotes
		public final boolean quoted;
		public final boolean negated; // - prefix
		public final boolean forced; // + prefix
		public final int start, end; // character span, including the prefix and quotes

		private Term(String text, boolean quoted, boolean negated, boolean forced, int start, int end){
			this.text=text;
			this.quoted=quoted;
			this.negated=negated;
			this.forced=forced;
			this.start=start;
			this.end=end;
		}
	}

	public static List<Term> tokenize(String query){
		ArrayList<Term> terms=new ArrayList<>();
		if(query==null)
			return terms;
		int i=0, n=query.length();
		while(i<n){
			while(i<n && Character.isWhitespace(query.charAt(i)))
				i++;
			if(i>=n)
				break;
			int start=i;
			char op=0;
			if(query.charAt(i)=='+' || query.charAt(i)=='-'){
				op=query.charAt(i);
				i++;
			}
			String text;
			boolean quoted=false;
			if(i<n && query.charAt(i)=='"'){
				quoted=true;
				i++;
				int j=query.indexOf('"', i);
				if(j<0){
					// Unterminated quote: the server drops the lone quote as junk and
					// searches the remaining text, so the remainder is term text.
					text=query.substring(i);
					i=n;
				}else{
					text=query.substring(i, j);
					i=j+1;
				}
			}else{
				int j=i;
				while(j<n && !Character.isWhitespace(query.charAt(j)))
					j++;
				text=query.substring(i, j);
				i=j;
			}
			terms.add(new Term(text, quoted, op=='-', op=='+', start, i));
		}
		return terms;
	}

	/**
	 * Whether the query contains the token as a standalone, positive operator: an exact
	 * term match that is not quoted (phrase text) and carries no - or + prefix. Quoted
	 * "has:media", -has:media and +has:media are different things on the server and do
	 * not light the chip, and neither do terms the server reads as plain words, like
	 * has:media. or (has:media).
	 */
	public static boolean containsToken(String query, String token){
		for(Term t : tokenize(query)){
			if(!t.quoted && !t.negated && !t.forced && t.text.equals(token))
				return true;
		}
		return false;
	}

	/**
	 * Appends the token, space-separated, at the end of the query. Existing text,
	 * including any trailing whitespace, is preserved exactly.
	 */
	public static String addToken(String query, String token){
		if(query.isEmpty())
			return token;
		if(Character.isWhitespace(query.charAt(query.length()-1)))
			return query+token;
		return query+" "+token;
	}

	/**
	 * Removes the token (every standalone, positive, unquoted occurrence) and collapses
	 * the separator the removal leaves behind. Nothing else in the query is touched:
	 * order, quoting and the user's own - / + prefixes survive, and a token that only
	 * appears inside a quoted phrase removes nothing at all.
	 */
	public static String removeToken(String query, String token){
		if(query==null)
			return null;
		List<Term> terms=tokenize(query);
		StringBuilder sb=new StringBuilder(query.length());
		int copyFrom=0;
		boolean found=false;
		for(Term t : terms){
			if(t.quoted || t.negated || t.forced || !t.text.equals(token))
				continue;
			found=true;
			int spanStart=t.start, spanEnd=t.end;
			boolean keptBefore=sb.length()>0 || query.substring(copyFrom, spanStart).trim().length()>0;
			if(keptBefore && Character.isWhitespace(query.charAt(spanStart-1))){
				spanStart--; // eat the separator before, keeping everything after intact
			}else if(!keptBefore){
				// Nothing before the token but whitespace: drop it all, then the separator after
				spanStart=copyFrom;
				while(spanEnd<query.length() && Character.isWhitespace(query.charAt(spanEnd)))
					spanEnd++;
			}
			sb.append(query, copyFrom, spanStart);
			copyFrom=spanEnd;
		}
		if(!found)
			return query;
		sb.append(query, copyFrom, query.length());
		return sb.toString();
	}

	/**
	 * Whether the server would accept this query as-is. Mirrors SearchQueryTransformer's
	 * validate_clauses!: in:library (a flag clause, valid with or without a sign), a
	 * positive from:me / from:<self>, or at least one non-negated keyword/phrase term.
	 * A quoted region is a PhraseClause and therefore a keyword — its content is never
	 * run through the prefix/colon classification.
	 */
	public static boolean isValidStandaloneQuery(String query){
		return isValidStandaloneQuery(query, null);
	}

	/**
	 * @param selfAcct the signed-in account's acct (username@domain), accepted as the
	 *                 second form of the from: self exception, with or without a
	 *                 leading @; null disables the check
	 */
	public static boolean isValidStandaloneQuery(String query, String selfAcct){
		if(query==null)
			return false;
		boolean inLibrary=false, selfFrom=false, hasKeyword=false;
		for(Term t : tokenize(query)){
			if(t.text.isEmpty())
				continue;
			if(t.quoted){
				if(!t.negated)
					hasKeyword=true;
				continue;
			}
			int colon=t.text.indexOf(':');
			if(colon>0){
				String prefix=t.text.substring(0, colon).toLowerCase(Locale.US);
				String value=t.text.substring(colon+1);
				if(SUPPORTED_PREFIXES.contains(prefix)){
					if(value.isEmpty()){
						// "has:" alone parses as the plain term "has" on the server
						if(!t.negated)
							hasKeyword=true;
					}else if(prefix.equals("in") && value.equals("library")){
						inLibrary=true; // in: is a flag clause on the server: sign-agnostic
					}else if(prefix.equals("from") && !t.negated && isSelfFrom(value, selfAcct)){
						selfFrom=true;
					}
					continue;
				}
			}
			// Plain word, hashtag or unknown prefix: a term. Only a positive one counts.
			if(!t.negated)
				hasKeyword=true;
		}
		return inLibrary || selfFrom || hasKeyword;
	}

	private static boolean isSelfFrom(String value, String selfAcct){
		if(value.equals("me"))
			return true;
		if(selfAcct==null)
			return false;
		String v=value.startsWith("@") ? value.substring(1) : value;
		// The server resolves accounts case-insensitively; the literal "me" above stays exact.
		return v.equalsIgnoreCase(selfAcct);
	}

	/**
	 * The full operator reference for the help sheet (§7), grouped, literal syntax only,
	 * followed by the keyword rule. Also what the Copy action puts on the clipboard.
	 */
	public static String buildHelpText(Context context){
		StringBuilder sb=new StringBuilder();
		appendGroup(sb, context, R.string.search_help_group_filters, R.string.search_help_filters);
		appendGroup(sb, context, R.string.search_help_group_people, R.string.search_help_people);
		appendGroup(sb, context, R.string.search_help_group_time, R.string.search_help_time);
		appendGroup(sb, context, R.string.search_help_group_language, R.string.search_help_language);
		appendGroup(sb, context, R.string.search_help_group_scope, R.string.search_help_scope);
		appendGroup(sb, context, R.string.search_help_group_syntax, R.string.search_help_syntax);
		sb.append(context.getString(R.string.search_help_keyword_rule));
		return sb.toString().trim();
	}

	private static void appendGroup(StringBuilder sb, Context context, int titleRes, int bodyRes){
		if(sb.length()>0)
			sb.append('\n');
		sb.append(context.getString(titleRes)).append('\n');
		sb.append(context.getString(bodyRes)).append('\n');
	}
}
