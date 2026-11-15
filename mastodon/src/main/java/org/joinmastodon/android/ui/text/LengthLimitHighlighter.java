package org.joinmastodon.android.ui.text;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;

import org.joinmastodon.android.R;
import org.joinmastodon.android.ui.utils.UiUtils;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LengthLimitHighlighter implements TextWatcher{
	private final Context context;
	private final int lengthLimit;
	private BackgroundColorSpan overLimitBG;
	private ForegroundColorSpan overLimitFG;
	private boolean isOverLimit;
	private OverLimitChangeListener listener;

	public LengthLimitHighlighter(Context context, int lengthLimit){
		this.context=context;
		overLimitBG=new BackgroundColorSpan(UiUtils.getThemeColor(context, R.attr.colorM3ErrorContainer));
		overLimitFG=new ForegroundColorSpan(UiUtils.getThemeColor(context, R.attr.colorM3Error));
		this.lengthLimit=lengthLimit;
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after){

	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count){

	}

	@Override
	public void afterTextChanged(Editable s){
		s.removeSpan(overLimitBG);
		s.removeSpan(overLimitFG);
		// The span start is a grapheme-boundary decision (astral emoji are
		// two UTF-16 units but one grapheme), never raw length arithmetic.
		int start=WeightedCount.plain(s.toString()).overLimitStart(lengthLimit);
		if(start>=0){
			s.setSpan(overLimitFG, start, s.length(), 0);
			s.setSpan(overLimitBG, start, s.length(), 0);
		}
		boolean newOverLimit=start>=0;
		if(newOverLimit!=isOverLimit){
			isOverLimit=newOverLimit;
			if(listener!=null)
				listener.onOverLimitChanged(isOverLimit);
		}
	}

	public LengthLimitHighlighter setListener(OverLimitChangeListener listener){
		this.listener=listener;
		return this;
	}

	public boolean isOverLimit(){
		return isOverLimit;
	}

	/**
	 * Audit R1 F3/F8 — the pure weighted-count core behind the composer
	 * counters and over-limit spans. The instance methods above only paint
	 * what this class computes; everything that decides WHERE a limit falls
	 * lives here so a plain JVM can pin it.
	 * <p>
	 * A {@code WeightedCount} is the old {@code countTextChars} pipeline
	 * (URLs collapse to {@value #URL_WEIGHT} units, mentions to
	 * {@code @local}, emoji to one unit) rebuilt on a (char, rawOffset) unit
	 * model so the weighted→raw offset mapping survives the three sequential
	 * replacement passes unchanged. {@link #overLimitStart(int)} therefore
	 * returns a raw offset that is always a valid span boundary — the audit
	 * crash was combined body+CW counts indexed into the body's range, which
	 * can go negative; that arithmetic is gone from the UI layer.
	 */
	public static class WeightedCount{
		/** URLs count the way Mastodon's limit expects. */
		public static final int URL_WEIGHT=23;

		/** Weighted grapheme count of the input. */
		public final int graphemes;
		/**
		 * {@code rawOffsetAt[k]} is the raw offset at the start of the
		 * k-th weighted grapheme (0-based); the last entry is
		 * {@code raw.length()}. Monotonically non-decreasing, never
		 * negative.
		 */
		private final int[] rawOffsetAt;

		private WeightedCount(int graphemes, int[] rawOffsetAt){
			this.graphemes=graphemes;
			this.rawOffsetAt=rawOffsetAt;
		}

		/**
		 * -1 when the count fits the limit; otherwise the raw offset where
		 * the over-limit region begins. Always within [0, raw length].
		 */
		public int overLimitStart(int limit){
			if(graphemes<=limit)
				return -1;
			return rawOffsetAt(limit);
		}

		/** Raw offset at the start of grapheme {@code k}; clamped at both ends. */
		public int rawOffsetAt(int k){
			return rawOffsetAt[Math.max(0, Math.min(k, rawOffsetAt.length-1))];
		}

		/** Plain grapheme count, no URL/mention/emoji weighting (CW fields, poll options). */
		public static int plainGraphemes(String raw){
			return plain(raw).graphemes;
		}

		/** Plain mapping: every raw unit keeps its own offset. */
		public static WeightedCount plain(String raw){
			UnitList units=new UnitList(raw.length());
			for(int i=0;i<raw.length();i++)
				units.add(raw.charAt(i), i);
			return fromUnits(units, raw.length());
		}

		/**
		 * The composer's weighted count. The three patterns and the
		 * replacement shapes mirror the old countTextChars replaceAll chain
		 * exactly (same patterns, same sequential order), so counts are
		 * unchanged; only the offset mapping is new.
		 */
		public static WeightedCount weighted(String raw, Pattern urlPattern, Pattern mentionPattern, Pattern emojiPattern){
			UnitList units=new UnitList(raw.length());
			for(int i=0;i<raw.length();i++)
				units.add(raw.charAt(i), i);
			// Pass 1 (URL): "$2" + 23 x's — group 2 is the preceding char.
			units=replaceAll(units, urlPattern, (out, in, m)->{
				out.appendPreserving(in, m.groupStart(2), m.groupEnd(2));
				int cut=in.rawOffsetAfter(m.end());
				for(int i=0;i<URL_WEIGHT;i++)
					out.add('x', cut);
			});
			// Pass 2 (mention): "$1@$3" — boundary, at-sign, local part.
			units=replaceAll(units, mentionPattern, (out, in, m)->{
				out.appendPreserving(in, m.groupStart(1), m.groupEnd(1));
				if(m.groupStart(2)>=0)
					out.add('@', in.rawOffsetAt(m.groupStart(2)));
				out.appendPreserving(in, m.groupStart(3), m.groupEnd(3));
			});
			// Pass 3 (emoji): one "x".
			units=replaceAll(units, emojiPattern, (out, in, m)->out.add('x', in.rawOffsetAfter(m.end())));
			return fromUnits(units, raw.length());
		}

		private static WeightedCount fromUnits(UnitList units, int rawLength){
			String mapped=units.asString();
			BreakIterator bi=BreakIterator.getCharacterInstance(Locale.ROOT);
			bi.setText(mapped);
			ArrayList<Integer> offsets=new ArrayList<>(mapped.length()+1);
			int start=0, end;
			while((end=bi.next())!=BreakIterator.DONE){
				offsets.add(units.src.get(start));
				start=end;
			}
			offsets.add(rawLength);
			int[] arr=new int[offsets.size()];
			for(int i=0;i<arr.length;i++)
				arr[i]=offsets.get(i);
			return new WeightedCount(offsets.size()-1, arr);
		}

		/**
		 * One global-replacement pass, replicating Matcher.replaceAll on the
		 * unit model: matches are found on the pass-input string, then each
		 * match is spliced out and the replacement units (carrying their raw
		 * cut offsets) spliced in.
		 */
		private static UnitList replaceAll(UnitList in, Pattern pattern, Replacement replacement){
			Matcher matcher=pattern.matcher(in.asString());
			if(!matcher.find())
				return in;
			ArrayList<Match> matches=new ArrayList<>();
			do{
				matches.add(new Match(matcher));
			}while(matcher.find());
			UnitList out=new UnitList(in.rawLength);
			int pos=0;
			for(Match m:matches){
				out.appendPreserving(in, pos, m.start());
				replacement.append(out, in, m);
				pos=m.end();
			}
			out.appendPreserving(in, pos, in.length());
			return out;
		}

		private interface Replacement{
			void append(UnitList out, UnitList in, Match m);
		}

		/** A match with group spans in unit (== pass-input char) coordinates. */
		private static class Match{
			private final int matchStart, matchEnd;
			private final int[] gStart, gEnd;

			Match(Matcher m){
				matchStart=m.start();
				matchEnd=m.end();
				int n=m.groupCount();
				gStart=new int[n+1];
				gEnd=new int[n+1];
				for(int i=0;i<=n;i++){
					gStart[i]=m.start(i);
					gEnd[i]=m.end(i);
				}
			}

			int start(){
				return matchStart;
			}

			int end(){
				return matchEnd;
			}

			int groupStart(int i){
				return gStart[i];
			}

			int groupEnd(int i){
				return gEnd[i];
			}
		}

		/** (char, rawOffset) units: the string being counted plus where each char came from. */
		private static class UnitList{
			final StringBuilder chars=new StringBuilder();
			final ArrayList<Integer> src=new ArrayList<>();
			final int rawLength;

			UnitList(int capacity){
				rawLength=capacity;
			}

			void add(char c, int rawOffset){
				chars.append(c);
				src.add(rawOffset);
			}

			int length(){
				return chars.length();
			}

			String asString(){
				return chars.toString();
			}

			/** Raw offset of the unit at char index i. */
			int rawOffsetAt(int i){
				return i<src.size() ? src.get(i) : rawLength;
			}

			/** Raw offset just past the unit range ending at char index i. */
			int rawOffsetAfter(int i){
				return i<src.size() ? src.get(i) : rawLength;
			}

			void appendPreserving(UnitList in, int from, int to){
				for(int i=from;i<to;i++)
					add(in.chars.charAt(i), in.src.get(i));
			}
		}
	}

	public interface OverLimitChangeListener{
		void onOverLimitChanged(boolean isOverLimit);
	}
}
