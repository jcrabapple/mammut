package org.joinmastodon.android.ui.text;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.MastodonApp;
import org.joinmastodon.android.R;

/**
 * FONT-SPEC §4 — the only class that knows which font resources the app
 * bundles. Everything else asks for a family and weight and never sees a
 * resource id: {@link #get} builds each face once through
 * {@link ResourcesCompat#getFont} and caches it, {@link #apply} maps a view's
 * current typeface (style flags included) onto the active family, and the
 * nested {@link InflaterFactory}/{@link LifecycleCallbacks} install the
 * app-wide application per Activity.
 *
 * The resource-name → family table of FONT-SPEC §2 lives in
 * {@link #resourceFor}; a rename in {@code res/font/} lands there as a 0 and a
 * W log line, never as a silent wrong family.
 */
public class MammutTypeface{
	private static final String TAG="MammutTypeface";

	// Slots in resourceFor(): regular, medium, bold, regular-italic. No family
	// ships a 500/700 italic file; those requests keep the upright medium/bold
	// file and the view synthesises the slant like the framework does for its
	// own fonts.
	private static final int SLOT_REGULAR=0, SLOT_MEDIUM=1, SLOT_BOLD=2, SLOT_ITALIC=3;

	/** (family.ordinal()<<2)|slot — faces are built once, never per bind.
	 * Lazily created: the class must stay pure-Java-initializable so
	 * resourceFor() is callable from JVM unit tests (§7), where the mockable
	 * android.jar constructors throw. */
	private static final Object CACHE_LOCK=new Object();
	private static SparseArray<Typeface> cache;

	private MammutTypeface(){}

	/**
	 * §4 style mapping for whatever family the reader picked. SYSTEM is a pure
	 * identity: no typeface object is built and no other work happens. The
	 * base's own style flags choose the file — bold flag → 700, resolved
	 * weight ≥ 500 (a sans-serif-medium style, which the constructor has
	 * already applied — §4.1) → 500, everything else → 400; the italic flag
	 * picks the 400-italic file. A load failure logs W and returns base.
	 */
	public static Typeface apply(Typeface base){
		return apply(GlobalUserPreferences.fontFamily, base, 0);
	}

	// Split from the public overload so the mapping is testable without the
	// global preference state.
	static Typeface apply(GlobalUserPreferences.FontFamily family, Typeface base){
		return apply(family, base, 0);
	}

	// Preference-reading overload carrying a pre-P weight hint (M3).
	static Typeface apply(Typeface base, int weightHint){
		return apply(GlobalUserPreferences.fontFamily, base, weightHint);
	}

	/**
	 * §4/M3: weightHint supplies a style-declared 500 for platforms without
	 * Typeface.getWeight() (API < 28) — see InflaterFactory.weightHint().
	 * A hint never overrides a bold flag or a getWeight() read; it only
	 * stands in for the medium weight the view was constructed with.
	 */
	static Typeface apply(GlobalUserPreferences.FontFamily family, Typeface base, int weightHint){
		if(family==GlobalUserPreferences.FontFamily.SYSTEM)
			return base;
		// M2-a: idempotency. A view already carrying one of the cached
		// bundled faces (the factory mapped it, a sheet pass re-runs, the
		// picker hand-set it) keeps it — below API 28 the weight resolution
		// can't read the bundled face back, so without this guard the
		// recursive pass would downgrade an already-medium view to 400.
		if(isBundled(base))
			return base;
		int style=base!=null ? base.getStyle() : Typeface.NORMAL;
		boolean bold=(style & Typeface.BOLD)!=0;
		boolean italic=(style & Typeface.ITALIC)!=0;
		int weight;
		if(bold){
			weight=700;
		}else if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P && base!=null && base.getWeight()>=500){
			weight=500;
		}else if(weightHint>=500){
			weight=500;
		}else{
			weight=400;
		}
		Typeface loaded=get(family, weight, italic);
		return loaded!=null ? loaded : base;
	}

	/**
	 * M2-a: identity scan over the lazily created face cache. Pure cache
	 * reads — never creates the cache, never loads a face — so the
	 * system-free path stays one preference read plus this scan per view.
	 */
	static boolean isBundled(Typeface base){
		if(base==null)
			return false;
		synchronized(CACHE_LOCK){
			if(cache==null)
				return false;
			for(int i=0;i<cache.size();i++)
				if(cache.valueAt(i)==base)
					return true;
			return false;
		}
	}

	/**
	 * §4.1/M2: walk an existing hierarchy and remap every TextView the way
	 * the factory would. The per-Activity InflaterFactory DOES see
	 * themed-context inflation — ContextThemeWrapper.cloneInContext's
	 * inflater copy carries mFactory2 (AOSP 8.0.0_r1 ContextThemeWrapper:167,
	 * LayoutInflater:216-222; same shape in 15.0.0_r1) — so XML content in
	 * dialogs and sheets was already covered at inflate time. This pass
	 * exists for views built programmatically after the factory ran
	 * (new CheckBox, new EditText, custom sheet rows — M2-b) and for the
	 * one BottomSheet without a constructor hook: AltTextSheet
	 * (ui/photoviewer), harmless today because its content is all XML.
	 * Safe to re-run over already-mapped content: apply() returns a
	 * bundled face unchanged (M2-a). The pre-P weight hint rides along so
	 * a sans-serif-medium dialog title keeps its 500 file.
	 */
	public static void applyRecursively(View root){
		if(root==null || GlobalUserPreferences.fontFamily==GlobalUserPreferences.FontFamily.SYSTEM)
			return;
		// The monospace guard's view-level branch is a no-op on API ≥ 28
		// (isMonospace(text, null) has no AttributeSet and the identity
		// check is pre-P only); the invariant there is held by nothing in
		// the app setting monospace at view level — code spans are spans,
		// which this hook never rewrites.
		if(root instanceof TextView text && !InflaterFactory.isMonospace(text, null)){
			Typeface base=text.getTypeface();
			Typeface font=apply(base, InflaterFactory.weightHint(base));
			if(font!=base)
				text.setTypeface(font);
		}
		if(root instanceof ViewGroup group){
			for(int i=0;i<group.getChildCount();i++)
				applyRecursively(group.getChildAt(i));
		}
	}

	/**
	 * §4: weight in {400, 500, 700}, cached in a SparseArray-keyed map and
	 * created once through ResourcesCompat.getFont. SYSTEM has no resource and
	 * returns null — the caller keeps the base it already has. A missing
	 * resource or a load failure logs W (with the family and weight) and
	 * returns null, which every caller turns into "keep the base".
	 */
	public static Typeface get(GlobalUserPreferences.FontFamily family, int weight, boolean italic){
		if(family==null || family==GlobalUserPreferences.FontFamily.SYSTEM)
			return null;
		int slot=weight>=650 ? SLOT_BOLD : weight>=450 ? SLOT_MEDIUM : SLOT_REGULAR;
		if(family==GlobalUserPreferences.FontFamily.ATKINSON && slot==SLOT_MEDIUM)
			slot=SLOT_BOLD; // §2: Atkinson ships no 500 — 500 maps to the 700 file
		if(italic && slot==SLOT_REGULAR)
			slot=SLOT_ITALIC;
		int key=(family.ordinal()<<2)|slot;
		synchronized(CACHE_LOCK){
			if(cache==null)
				cache=new SparseArray<>();
			Typeface cached=cache.get(key);
			if(cached!=null)
				return cached;
			int res=resourceFor(family, slot);
			if(res==0){
				Log.w(TAG, "No bundled font for family "+family+" (weight "+weight+(italic ? ", italic" : "")+")");
				return null;
			}
			Typeface loaded=null;
			try{
				Context ctx=MastodonApp.context;
				loaded=ResourcesCompat.getFont(ctx, res);
				if(loaded==null)
					Log.w(TAG, "Failed to load font resource "+ctx.getResources().getResourceEntryName(res)+" ("+family+")");
			}catch(Throwable t){
				Log.w(TAG, "Failed to load font resource for family "+family+" (weight "+weight+(italic ? ", italic" : "")+")", t);
			}
			if(loaded==null)
				return null;
			cache.put(key, loaded);
			return loaded;
		}
	}

	/**
	 * FONT-SPEC §2's resource-name → family table. Returns 0 when nothing is
	 * bundled for the slot, which get() reports instead of silently rendering
	 * the system face.
	 */
	static int resourceFor(GlobalUserPreferences.FontFamily family, int slot){
		return switch(family){
			case INTER -> switch(slot){
				case SLOT_REGULAR -> R.font.inter_regular;
				case SLOT_MEDIUM -> R.font.inter_medium;
				case SLOT_BOLD -> R.font.inter_bold;
				case SLOT_ITALIC -> R.font.inter_italic;
				default -> 0;
			};
			case SOURCE_SANS -> switch(slot){
				case SLOT_REGULAR -> R.font.source_sans_regular;
				case SLOT_MEDIUM -> R.font.source_sans_medium;
				case SLOT_BOLD -> R.font.source_sans_bold;
				case SLOT_ITALIC -> R.font.source_sans_italic;
				default -> 0;
			};
			case LITERATA -> switch(slot){
				case SLOT_REGULAR -> R.font.literata_regular;
				case SLOT_MEDIUM -> R.font.literata_medium;
				case SLOT_BOLD -> R.font.literata_bold;
				case SLOT_ITALIC -> R.font.literata_italic;
				default -> 0;
			};
			case ATKINSON -> switch(slot){
				case SLOT_REGULAR -> R.font.atkinson_regular;
				case SLOT_MEDIUM -> R.font.atkinson_bold; // §2: 500 → 700
				case SLOT_BOLD -> R.font.atkinson_bold;
				case SLOT_ITALIC -> R.font.atkinson_italic;
				default -> 0;
			};
			case SYSTEM -> 0;
		};
	}

	/**
	 * §4.1: the per-Activity LayoutInflater.Factory2. The factory replicates
	 * the framework's tag→class resolution (the same prefix list
	 * PhoneLayoutInflater walks) so it holds the constructed view and can
	 * post-process it after the constructor has applied the style's own
	 * fontFamily; returning null hands the tag back to the framework, which
	 * resolves it the same way. Span-level typefaces are never touched — this
	 * sets the view's base face only, so code spans keep their monospace span.
	 */
	public static class InflaterFactory implements LayoutInflater.Factory2{
		private static final String ANDROID_NS="http://schemas.android.com/apk/res/android";
		private static final String[] PREFIXES={"android.widget.", "android.webkit.", "android.app."};

		public InflaterFactory(){}

		@Override
		public View onCreateView(View parent, String name, Context context, AttributeSet attrs){
			// §4.1/§5: SYSTEM is the zero-cost path — one preference read per
			// inflated tag, then the framework does exactly what it did before.
			if(GlobalUserPreferences.fontFamily==GlobalUserPreferences.FontFamily.SYSTEM)
				return null;
			View view=createView(context, name, attrs);
			if(view instanceof TextView text && !isMonospace(text, attrs)){
				Typeface base=text.getTypeface();
				Typeface font=MammutTypeface.apply(base, weightHint(attrs, base));
				if(font!=base)
					text.setTypeface(font);
			}
			return view;
		}

		@Override
		public View onCreateView(String name, Context context, AttributeSet attrs){
			return onCreateView(null, name, context, attrs);
		}

		private View createView(Context context, String name, AttributeSet attrs){
			// M4: construct with the context the framework hands this
			// Factory2 — for themed wrappers (dialogs, sheets) that is the
			// clone currently inflating, so its constructor args already
			// hold the wrapper's theme overlay. Using the field inflater
			// (the Activity's) would drop the overlay: dialog button bars
			// fell back to the platform all-caps style.
			LayoutInflater themedInflater=LayoutInflater.from(context);
			try{
				if(name.indexOf('.')>=0)
					return themedInflater.createView(name, null, attrs);
			}catch(ClassNotFoundException ignored){
				return null; // not a real class — the framework reports it the same way
			}catch(InflateException e){
				// A constructor failure the framework would also hit — let it
				// surface identically instead of swallowing the tag.
				throw e;
			}
			for(String prefix:PREFIXES){
				try{
					return themedInflater.createView(name, prefix, attrs);
				}catch(ClassNotFoundException ignored){}
			}
			return null;
		}

		/**
		 * §4.1: leave anything carrying monospace alone. Catches the declared
		 * cases (android:fontFamily="monospace", android:typeface="monospace",
		 * the latter being 3 = TYPEFACE_MONOSPACE). applyRecursively passes no
		 * AttributeSet; on API < 28 the framework resolved a view-level
		 * family through the Typeface.create cache, so an identity match
		 * against the cached monospace instance for the same style detects
		 * it there. Nothing in the app's styles or layouts sets monospace at
		 * view level — code spans are spans, which this hook never rewrites.
		 */
		private static boolean isMonospace(TextView view, AttributeSet attrs){
			if(attrs!=null){
				if("monospace".equals(attrs.getAttributeValue(ANDROID_NS, "fontFamily")))
					return true;
				if(attrs.getAttributeIntValue(ANDROID_NS, "typeface", -1)==3)
					return true;
			}
			if(Build.VERSION.SDK_INT<Build.VERSION_CODES.P && view.getTypeface()!=null)
				return Typeface.create("monospace", view.getTypeface().getStyle())==view.getTypeface();
			return false;
		}

		/**
		 * M3: API < 28 has no Typeface.getWeight(), so a style-declared
		 * "sans-serif-medium" can't be read back from the constructed view's
		 * typeface. The mechanism is the pre-P Typeface.create cache: AOSP
		 * TextView.setTypefaceFromAttrs builds view typefaces through
		 * Typeface.create(name, style) (6.0.1_r1 TextView:1492,
		 * 8.0.0_r1 TextView:2101), which returns the identical cached
		 * instance for the same name+style — so an identity check against
		 * Typeface.create("sans-serif-medium", style) recovers a style- or
		 * theme-declared medium face (m3_title/label styles, android:buttonStyle,
		 * which styles.xml sets to sans-serif-medium) exactly. The explicit
		 * android:fontFamily attribute is checked first; an
		 * obtainStyledAttributes(..., defStyleAttr=textAppearance, ...) branch
		 * used to live here but resolved the THEME's textAppearance, never
		 * the view's, so it could never fire and was removed. A platform
		 * that doesn't share that cache simply reports no hint and the view
		 * keeps its 400 file.
		 */
		private static int weightHint(AttributeSet attrs, Typeface base){
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P)
				return 0; // apply() reads getWeight() directly
			String ff=attrs.getAttributeValue(ANDROID_NS, "fontFamily");
			if("sans-serif-medium".equals(ff))
				return 500;
			return weightHint(base);
		}

		// Attribute-free half of the hint, shared with applyRecursively()
		// (dialogs and sheets carry textAppearance styles too).
		private static int weightHint(Typeface base){
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P || base==null)
				return 0;
			return Typeface.create("sans-serif-medium", base.getStyle())==base ? 500 : 0;
		}
	}

	/**
	 * §4.1: installs one {@link InflaterFactory} per Activity via
	 * onActivityCreated, after the activity's own setContentView ran. If the
	 * inflater was already frozen by a framework call, that Activity logs one
	 * W line and carries on with the system face.
	 */
	public static class LifecycleCallbacks implements Application.ActivityLifecycleCallbacks{
		@Override
		public void onActivityCreated(Activity activity, Bundle savedInstanceState){
			try{
				LayoutInflater inflater=activity.getLayoutInflater();
				inflater.setFactory2(new InflaterFactory());
			}catch(Throwable t){
				Log.w(TAG, "Could not install typeface factory in "+activity.getClass().getSimpleName()+" — carrying on with the system face", t);
			}
		}

		@Override public void onActivityStarted(Activity activity){}

		@Override public void onActivityResumed(Activity activity){}

		@Override public void onActivityPaused(Activity activity){}

		@Override public void onActivityStopped(Activity activity){}

		@Override public void onActivitySaveInstanceState(Activity activity, Bundle outState){}

		@Override public void onActivityDestroyed(Activity activity){}
	}
}
