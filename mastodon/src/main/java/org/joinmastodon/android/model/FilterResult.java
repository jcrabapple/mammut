package org.joinmastodon.android.model;

import org.joinmastodon.android.api.ObjectValidationException;
import org.joinmastodon.android.api.RequiredField;
import org.parceler.Parcel;

import java.util.List;

import androidx.annotation.Nullable;

@Parcel
public class FilterResult extends BaseModel{
	@RequiredField
	public Filter filter;

	public List<String> keywordMatches;

	@Override
	public void postprocess() throws ObjectValidationException{
		super.postprocess();
		filter.postprocess();
	}

	// MAMMUT: F2 — single place that decides how a set of filter results presents.
	// Precedence is Hide > Warn > Blur. A null action (absent field or an unrecognized future
	// wire value, which the FilterAction adapter decodes as null) is treated as Warn.
	// Returns null when no active result exists.
	@Nullable
	public static FilterResult getEffectiveResult(@Nullable List<FilterResult> results){
		if(results==null)
			return null;
		FilterResult warn=null, blur=null;
		for(FilterResult result:results){
			if(result==null || result.filter==null || !result.filter.isActive())
				continue;
			FilterAction action=result.filter.filterAction;
			if(action==null)
				action=FilterAction.WARN;
			switch(action){
				case HIDE -> {
					return result;
				}
				case WARN -> {
					if(warn==null)
						warn=result;
				}
				case BLUR -> {
					if(blur==null)
						blur=result;
				}
			}
		}
		return warn!=null ? warn : blur;
	}
}
