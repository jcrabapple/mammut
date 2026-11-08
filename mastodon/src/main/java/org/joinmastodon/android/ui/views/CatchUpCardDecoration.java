package org.joinmastodon.android.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import org.joinmastodon.android.R;
import org.joinmastodon.android.ui.utils.UiUtils;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import me.grishka.appkit.utils.V;

/**
 * MAMMUT: Catch up (docs/CATCH-UP-SPEC.md) — draws one elevated card behind each author group.
 *
 * Catch up is a reading view built from ordinary recycled status rows, so a card cannot be a
 * container view: the rows are not owned by any wrapper. This decoration paints the card in the
 * canvas behind a contiguous run of adapter positions that share a group id, insets those rows
 * horizontally so their content sits inside the card, and adds the vertical gap between cards.
 *
 * The soft edge is three progressively larger, very faint rounded rectangles rather than a real
 * elevation shadow, because a real one would need a software layer on the whole list and that
 * costs scrolling smoothness on long catch-ups.
 */
public class CatchUpCardDecoration extends RecyclerView.ItemDecoration{
	private final Paint fillPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint outlinePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint[] shadowPaints=new Paint[3];
	private final float radius;
	private final int cardInset;
	private final int gap;
	private final RectF rect=new RectF();

	/** position -> group id; negative means "not inside a card". */
	private int[] groups=new int[0];

	public CatchUpCardDecoration(Context context){
		radius=V.dp(20);
		cardInset=V.dp(8);
		gap=V.dp(12);

		// A shade below the theme's surface variant: the card should sit on the page, not
		// merely tint it. Blending toward black works in both themes, where a fixed colour
		// would only work in one.
		int base=UiUtils.getThemeColor(context, R.attr.colorM3SurfaceVariant);
		fillPaint.setColor(Color.rgb(
				(int)(Color.red(base)*0.93f),
				(int)(Color.green(base)*0.93f),
				(int)(Color.blue(base)*0.93f)
		));
		outlinePaint.setStyle(Paint.Style.STROKE);
		outlinePaint.setStrokeWidth(V.dp(1));
		outlinePaint.setColor(Color.argb(0x66,
				Color.red(UiUtils.getThemeColor(context, R.attr.colorM3Outline)),
				Color.green(UiUtils.getThemeColor(context, R.attr.colorM3Outline)),
				Color.blue(UiUtils.getThemeColor(context, R.attr.colorM3Outline))
		));
		for(int i=0; i<shadowPaints.length; i++){
			shadowPaints[i]=new Paint(Paint.ANTI_ALIAS_FLAG);
			shadowPaints[i].setColor(0x00000000);
		}
	}

	private static int darken(int color, float amount){
		int r=Math.round(Color.red(color)*(1-amount));
		int g=Math.round(Color.green(color)*(1-amount));
		int b=Math.round(Color.blue(color)*(1-amount));
		return Color.argb(Color.alpha(color), r, g, b);
	}

	public void setGroups(@NonNull int[] groups){
		this.groups=groups;
	}

	private int groupAt(int position){
		return position>=0 && position<groups.length ? groups[position] : -1;
	}

	@Override
	public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state){
		int pos=parent.getChildAdapterPosition(view);
		int g=groupAt(pos);
		if(g<0)
			return;
		outRect.left=cardInset;
		outRect.right=cardInset;
		if(pos==0 || groupAt(pos-1)!=g)
			outRect.top+=gap;
		if(groupAt(pos+1)!=g)
			outRect.bottom+=gap/2;
	}

	@Override
	public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state){
		int childCount=parent.getChildCount();
		int i=0;
		while(i<childCount){
			View first=parent.getChildAt(i);
			int pos=parent.getChildAdapterPosition(first);
			int g=groupAt(pos);
			if(g<0){
				i++;
				continue;
			}
			// Find the whole contiguous run for this group. Rows are laid out in order, so a
			// card is simply the first child's top to the last child's bottom.
			int end=i;
			while(end+1<childCount){
				int nextPos=parent.getChildAdapterPosition(parent.getChildAt(end+1));
				if(groupAt(nextPos)!=g)
					break;
				end++;
			}
			View last=parent.getChildAt(end);
			rect.set(cardInset, first.getTop(), parent.getWidth()-cardInset, last.getBottom());
			drawCard(c, parent);
			i=end+1;
		}
	}

	private void drawCard(Canvas c, RecyclerView parent){
		// Faint layers, each a little larger and lower, read as a soft edge without a layer type.
		float[] spread={V.dp(4), V.dp(2.5f), V.dp(1.25f)};
		int[] color={0x0E000000, 0x12000000, 0x1C000000};
		for(int i=0; i<spread.length; i++){
			shadowPaints[i].setColor(color[i]);
			RectF s=new RectF(rect);
			s.inset(-spread[i], -spread[i]/2);
			s.offset(0, spread[i]/2);
			c.drawRoundRect(s, radius+spread[i], radius+spread[i], shadowPaints[i]);
		}
		c.drawRoundRect(rect, radius, radius, fillPaint);
		RectF o=new RectF(rect);
		o.inset(V.dp(0.5f), V.dp(0.5f));
		c.drawRoundRect(o, radius, radius, outlinePaint);
	}
}
