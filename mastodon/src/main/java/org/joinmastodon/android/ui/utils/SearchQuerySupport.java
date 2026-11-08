package org.joinmastodon.android.ui.utils;

import android.content.Context;

import org.joinmastodon.android.R;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Helpers for the search operator chips (G11). Everything is derived from the query text;
 * nothing is stored. The validity rule mirrors Mastodon's own SearchQueryTransformer:
 * a query made only of operators is rejected ("At least one keyword or phrase is required")
 * unless it scopes to the library (in:library) or to the user's own posts (from:me).
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
	 * Matches an exact prefix:value token at a word boundary. A leading - or + makes it a
	 * different filter (exclusion / forced inclusion), and quotes make it phrase text, so
	 * neither lights the positive chip: -has:media, +has:media and "has:media" do not match.
	 * A trailing word character or colon excludes has:mediax and has:media:foo.
	 */
	private static String tokenRegex(String token){
		return "(?<![-\\w+:\"])\\Q"+token+"\\E(?![\\w:\"])";
	}

	public static boolean containsToken(String query, String token){
		return query!=null && query.matches("(?s).*"+tokenRegex(token)+".*");
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
	 * Removes the last occurrence of the exact token and collapses the space the removal
	 * leaves behind. Nothing else in the query is touched: order, quoting and the user's
	 * own +/- prefixes survive.
	 */
	public static String removeToken(String query, String token){
		java.util.regex.Matcher m=java.util.regex.Pattern.compile(tokenRegex(token)).matcher(query);
		int start=-1, end=-1;
		while(m.find()){
			start=m.start();
			end=m.end();
		}
		if(start<0)
			return query;
		String before=query.substring(0, start), after=query.substring(end);
		if(before.isEmpty()){
			int i=0;
			while(i<after.length() && after.charAt(i)==' ')
				i++;
			return after.substring(i);
		}
		if(after.isEmpty()){
			int i=before.length();
			while(i>0 && before.charAt(i-1)==' ')
				i--;
			return before.substring(0, i);
		}
		if(before.charAt(before.length()-1)==' ' && after.charAt(0)==' ')
			before=before.substring(0, before.length()-1);
		return before+after;
	}

	/**
	 * Whether the server would accept this query as-is. Mirrors SearchQueryTransformer:
	 * in:library, a positive from:me, or at least one non-negated keyword/phrase term.
	 */
	public static boolean isValidStandaloneQuery(String query){
		if(query==null)
			return false;
		boolean inLibrary=false, selfFrom=false, hasKeyword=false;
		int i=0, n=query.length();
		while(i<n){
			while(i<n && Character.isWhitespace(query.charAt(i)))
				i++;
			if(i>=n)
				break;
			char op=0;
			if(query.charAt(i)=='+' || query.charAt(i)=='-'){
				op=query.charAt(i);
				i++;
			}
			String term;
			if(i<n && query.charAt(i)=='"'){
				int j=query.indexOf('"', i+1);
				if(j<0)
					break; // unbalanced quote: the server fails to parse; whatever we have so far stands
				term=query.substring(i+1, j);
				i=j+1;
			}else{
				int j=i;
				while(j<n && !Character.isWhitespace(query.charAt(j)))
					j++;
				term=query.substring(i, j);
				i=j;
			}
			if(term.isEmpty())
				continue;
			int colon=term.indexOf(':');
			if(colon>0){
				String prefix=term.substring(0, colon).toLowerCase(Locale.US);
				String value=term.substring(colon+1);
				if(SUPPORTED_PREFIXES.contains(prefix)){
					if(value.isEmpty()){
						// "has:" alone parses as the plain term "has" on the server
						if(op!='-')
							hasKeyword=true;
					}else if(prefix.equals("in") && value.equals("library")){
						inLibrary=true;
					}else if(prefix.equals("from") && value.equals("me") && op!='-'){
						selfFrom=true;
					}
					continue;
				}
			}
			// Plain word, hashtag, unknown prefix or phrase: a term. Only a positive one counts.
			if(op!='-')
				hasKeyword=true;
		}
		return inLibrary || selfFrom || hasKeyword;
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
